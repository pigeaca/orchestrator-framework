package com.application.orchestrator.service.activity.impl

import com.application.orchestrator.config.QueueConfig
import com.application.orchestrator.data.OrchestratorWorkflowStepData
import com.application.orchestrator.data.WorkflowLockManager
import com.application.orchestrator.engine.StepStatus
import com.application.orchestrator.service.activity.ActivityQueueManager
import com.application.orchestrator.util.ValidationUtils
import com.orchestrator.proto.ActivityTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Implementation of ActivityQueueManager with backpressure handling.
 * Manages activity task queues using bounded queues to prevent memory exhaustion under high load.
 * Implements backpressure mechanisms to handle queue overflow situations.
 */
@Service
class ActivityQueueManagerImpl(
    private val lockManager: WorkflowLockManager,
    private val orchestratorWorkflowStepData: OrchestratorWorkflowStepData,
    private val queueProperties: QueueConfig.ActivityQueueProperties
) : ActivityQueueManager {
    private val logger = LoggerFactory.getLogger(ActivityQueueManagerImpl::class.java)

    private val activityTasks = ConcurrentHashMap<String, LinkedBlockingQueue<ActivityTask>>()

    /**
     * Initializes the activity queues.
     *
     * @param queues The collection of queue names to initialize
     * @throws IllegalArgumentException if queues is null or empty
     */
    override suspend fun init(queues: Collection<String>) {
        ValidationUtils.validateNotEmpty(queues, "queues")
        logger.debug("Initializing ${queues.size} activity queues")

        queues.forEach { queue ->
            ValidationUtils.validateNotEmpty(queue, "queue name")
            activityTasks.computeIfAbsent(queue) { LinkedBlockingQueue<ActivityTask>(queueProperties.capacity) }
        }
    }

    /**
     * Polls for an activity task from the specified queue.
     * Uses thread-safe operations to retrieve tasks from the queue.
     * This method is maintained for backward compatibility.
     *
     * @param queue The queue to poll from
     * @return The activity task or null if no task is available
     * @throws IllegalArgumentException if queue is empty
     */
    override suspend fun pollTask(queue: String): ActivityTask? {
        ValidationUtils.validateNotEmpty(queue, "queue")

        val taskQueue = activityTasks[queue] ?: return null
        val activityTask = taskQueue.poll() ?: return null

        logger.debug("Polled task from queue $queue: $activityTask")

        val acquired = lockManager.tryAcquire(activityTask.sagaId, "ActivityQueueManager", Duration.ofSeconds(30))
        if (!acquired) {
            logger.debug("Could not acquire lock for workflow ${activityTask.sagaId}")
            // Put the task back in the queue if we couldn't acquire the lock
            taskQueue.offer(activityTask)
            return null
        }

        try {
            orchestratorWorkflowStepData.updateStepStatus(
                workflowId = activityTask.sagaId,
                stepId = activityTask.stepId,
                status = StepStatus.IN_PROGRESS
            )
            return activityTask
        } finally {
            lockManager.release(activityTask.sagaId, "ActivityQueueManager")
        }
    }

    /**
     * Polls for multiple activity tasks from the specified queue.
     * Enables batch processing for improved throughput.
     * Uses thread-safe operations to retrieve tasks from the queue.
     *
     * @param queue The queue to poll from
     * @param batchSize The maximum number of tasks to poll
     * @return A list of activity tasks, may be empty if no tasks are available
     * @throws IllegalArgumentException if queue is empty or batchSize is not positive
     */
    override suspend fun pollTasks(queue: String, batchSize: Int): List<ActivityTask> {
        ValidationUtils.validateNotEmpty(queue, "queue")
        ValidationUtils.validatePositive(batchSize, "batchSize")

        val taskQueue = activityTasks[queue] ?: return emptyList()

        val tempTasks = mutableListOf<ActivityTask>()
        taskQueue.drainTo(tempTasks, batchSize)

        if (tempTasks.isEmpty()) {
            return emptyList()
        }

        logger.debug("Polled ${tempTasks.size} tasks from queue $queue")

        // Group tasks by workflow ID to minimize lock acquisitions
        val tasksByWorkflow = tempTasks.groupBy { it.sagaId }
        val resultTasks = mutableListOf<ActivityTask>()

        // Process each workflow's tasks under a single lock
        for ((workflowId, workflowTasks) in tasksByWorkflow) {
            val acquired = lockManager.tryAcquire(workflowId, "ActivityQueueManager", Duration.ofSeconds(30))
            if (!acquired) {
                logger.debug("Could not acquire lock for workflow $workflowId, returning ${workflowTasks.size} tasks to queue")
                // Put the tasks back in the queue if we couldn't acquire the lock
                workflowTasks.forEach { taskQueue.offer(it) }
                continue
            }

            try {
                // Update status for all tasks in this workflow
                for (task in workflowTasks) {
                    orchestratorWorkflowStepData.updateStepStatus(
                        workflowId = task.sagaId,
                        stepId = task.stepId,
                        status = StepStatus.IN_PROGRESS
                    )
                    resultTasks.add(task)
                }
            } finally {
                lockManager.release(workflowId, "ActivityQueueManager")
            }
        }

        logger.debug("Successfully processed ${resultTasks.size} tasks from queue $queue")
        return resultTasks
    }

    /**
     * Submits an activity task to its queue with backpressure handling.
     * If the queue is full, it will attempt to add the task with a timeout.
     * If the task cannot be added within the timeout, it will be rejected.
     *
     * @param task The activity task to submit
     * @throws IllegalArgumentException if task is null
     */
    override suspend fun submitTask(task: ActivityTask) {
        ValidationUtils.validateNotNull(task, "task")
        ValidationUtils.validateNotEmpty(task.queue, "task.queue")

        val queueName = task.queue
        val taskQueue = activityTasks[queueName]

        if (taskQueue == null) {
            logger.warn("Queue $queueName not initialized, initializing now")
            activityTasks.computeIfAbsent(queueName) { LinkedBlockingQueue<ActivityTask>(queueProperties.capacity) }
        }


        // Try to add the task with a timeout to implement backpressure
        val added =
            activityTasks[queueName]?.offer(task, queueProperties.offerTimeoutMs, TimeUnit.MILLISECONDS) ?: false

        if (!added) {

            logger.warn(
                "Task rejected due to queue overflow. Queue: $queueName, Size: ${activityTasks[queueName]?.size}, " +
                        "Capacity: ${queueProperties.capacity}"
            )
        } else {
            logger.debug("Task added to queue $queueName. Current size: ${activityTasks[queueName]?.size}/${queueProperties.capacity}")
        }
    }

    /**
     * Submits multiple activity tasks to their respective queues.
     * Enables batch processing for improved throughput.
     * Implements backpressure handling for each queue.
     *
     * @param tasks The list of activity tasks to submit
     * @throws IllegalArgumentException if tasks is null or empty
     */
    override suspend fun submitTasks(tasks: List<ActivityTask>) {
        ValidationUtils.validateNotEmpty(tasks, "tasks")

        // Group tasks by queue for more efficient processing
        val tasksByQueue = tasks.groupBy { it.queue }

        // Process each queue's tasks
        for ((queueName, queueTasks) in tasksByQueue) {
            ValidationUtils.validateNotEmpty(queueName, "queue name")

            // Ensure the queue exists
            val taskQueue = activityTasks[queueName]
            if (taskQueue == null) {
                logger.warn("Queue $queueName not initialized, initializing now")
                activityTasks.computeIfAbsent(queueName) { LinkedBlockingQueue<ActivityTask>(queueProperties.capacity) }
            }


            // Track accepted and rejected tasks
            var acceptedCount = 0
            var rejectedCount = 0

            // Try to add each task with a timeout to implement backpressure
            for (task in queueTasks) {
                val added = activityTasks[queueName]?.offer(task, queueProperties.offerTimeoutMs, TimeUnit.MILLISECONDS)
                    ?: false

                if (added) {
                    acceptedCount++
                } else {
                    rejectedCount++
                }
            }

            // Update metrics and log results
            if (rejectedCount > 0) {
                logger.warn(
                    "$rejectedCount/${queueTasks.size} tasks rejected due to queue overflow. Queue: $queueName, " +
                            "Capacity: ${queueProperties.capacity}"
                )
            }

            if (acceptedCount > 0) {
                logger.debug("$acceptedCount tasks added to queue $queueName. Current size: ${activityTasks[queueName]?.size}/${queueProperties.capacity}")
            }
        }
    }
}
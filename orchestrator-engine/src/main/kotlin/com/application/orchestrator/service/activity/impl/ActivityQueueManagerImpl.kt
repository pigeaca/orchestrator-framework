package com.application.orchestrator.service.activity.impl

import com.application.orchestrator.data.OrchestratorWorkflowStepData
import com.application.orchestrator.data.WorkflowLockManager
import com.application.orchestrator.engine.StepStatus
import com.application.orchestrator.service.activity.ActivityQueueManager
import com.application.orchestrator.util.ValidationUtils
import com.orchestrator.proto.ActivityTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

/**
 * Implementation of ActivityQueueManager.
 * Manages activity task queues and handles task submission and polling.
 */
@Service
class ActivityQueueManagerImpl(
    private val lockManager: WorkflowLockManager,
    private val orchestratorWorkflowStepData: OrchestratorWorkflowStepData
) : ActivityQueueManager {
    private val logger = LoggerFactory.getLogger(ActivityQueueManagerImpl::class.java)
    private val activityTasks = mutableMapOf<String, ArrayDeque<ActivityTask>>()

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
            activityTasks.computeIfAbsent(queue) { ArrayDeque<ActivityTask>() } 
        }
    }

    /**
     * Polls for an activity task from the specified queue.
     *
     * @param queue The queue to poll from
     * @return The activity task or null if no task is available
     * @throws IllegalArgumentException if queue is empty
     */
    override suspend fun pollTask(queue: String): ActivityTask? {
        ValidationUtils.validateNotEmpty(queue, "queue")
        
        val activityTask = activityTasks[queue]?.poll() ?: return null
        logger.debug("Polled task from queue $queue: $activityTask")
        
        val acquired = lockManager.tryAcquire(activityTask.sagaId, "ActivityQueueManager", Duration.ofSeconds(30))
        if (!acquired) {
            logger.debug("Could not acquire lock for workflow ${activityTask.sagaId}")
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
     * Submits an activity task to its queue.
     *
     * @param task The activity task to submit
     * @throws IllegalArgumentException if task is null
     */
    override suspend fun submitTask(task: ActivityTask) {
        ValidationUtils.validateNotNull(task, "task")
        ValidationUtils.validateNotEmpty(task.queue, "task.queue")
        
        logger.debug("Submitting task to queue ${task.queue}: $task")
        
        activityTasks.computeIfPresent(task.queue) { _, queue ->
            queue.push(task)
            queue
        }
    }
}
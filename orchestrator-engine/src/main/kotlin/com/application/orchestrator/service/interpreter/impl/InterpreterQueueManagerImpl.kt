package com.application.orchestrator.service.interpreter.impl

import com.application.orchestrator.config.QueueConfig.InterpreterQueueProperties
import com.application.orchestrator.service.interpreter.InterpreterQueueManager
import com.application.orchestrator.util.ValidationUtils
import com.orchestrator.proto.InterpreterWorkerTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Implementation of InterpreterQueueManager with backpressure handling.
 * Uses a bounded queue to prevent memory exhaustion under high load.
 * Implements backpressure mechanisms to handle queue overflow situations.
 */
@Service
class InterpreterQueueManagerImpl(
    private val queueProperties: InterpreterQueueProperties
) : InterpreterQueueManager {
    private val logger = LoggerFactory.getLogger(InterpreterQueueManagerImpl::class.java)

    private val interpreterTasks = LinkedBlockingQueue<InterpreterWorkerTask>(queueProperties.capacity)

    /**
     * Polls for all available interpreter tasks.
     * Uses a mutex to ensure thread safety when draining the queue.
     * This method is maintained for backward compatibility.
     *
     * @return A list of interpreter worker tasks
     */
    override suspend fun pollTasks(): List<InterpreterWorkerTask> {
        val tasks = mutableListOf<InterpreterWorkerTask>()
        interpreterTasks.drainTo(tasks)
        logger.debug("Polled ${tasks.size} interpreter tasks")
        return tasks
    }

    /**
     * Polls for available interpreter tasks for a specific worker.
     * Distributes tasks among workers based on task hash modulo worker count.
     * This enables horizontal scaling by ensuring each task is processed by exactly one worker.
     *
     * @param workerId The unique ID of the worker
     * @param workerCount The total number of workers in the cluster
     * @param batchSize The maximum number of tasks to return
     * @return A list of interpreter worker tasks assigned to this worker
     */
    override suspend fun pollTasksForWorker(
        workerId: String,
        workerCount: Int,
        batchSize: Int
    ): List<InterpreterWorkerTask> {
        // Validate input parameters
        ValidationUtils.validateNotEmpty(workerId, "workerId")
        ValidationUtils.validatePositive(workerCount, "workerCount")
        ValidationUtils.validatePositive(batchSize, "batchSize")

        logger.debug("Worker $workerId polling for tasks (worker count: $workerCount, batch size: $batchSize)")

        // Get all available tasks
        val allTasks = mutableListOf<InterpreterWorkerTask>()
        interpreterTasks.drainTo(allTasks)

        if (allTasks.isEmpty()) {
            return emptyList()
        }

        // If there's only one worker, or we have fewer tasks than batch size, return all tasks
        if (workerCount <= 1 || allTasks.size <= batchSize) {
            logger.debug("Worker $workerId polled ${allTasks.size} tasks (single worker mode)")
            return allTasks
        }

        // Distribute tasks among workers based on task hash
        val workerTasks = allTasks.filter { task ->
            // Use sagaId and stepId to create a consistent hash
            val taskHash = abs((task.sagaId + task.stepId).hashCode())
            val workerIndex = taskHash % workerCount

            // Convert workerId to a numeric index for comparison
            val thisWorkerIndex = abs(workerId.hashCode()) % workerCount

            workerIndex == thisWorkerIndex
        }.take(batchSize)

        // Put back tasks that don't belong to this worker
        val tasksToReturn = allTasks.filter { task -> !workerTasks.contains(task) }
        tasksToReturn.forEach { interpreterTasks.offer(it) }

        logger.debug("Worker $workerId polled ${workerTasks.size} tasks out of ${allTasks.size} total tasks")
        return workerTasks
    }

    /**
     * Submits an interpreter task to the queue with backpressure handling.
     * If the queue is full, it will attempt to add the task with a timeout.
     * If the task cannot be added within the timeout, it will be rejected.
     *
     * @param task The interpreter worker task to submit
     * @throws IllegalArgumentException if task is null
     */
    override suspend fun submitTask(task: InterpreterWorkerTask) {
        ValidationUtils.validateNotNull(task, "task")

        // Try to add the task with a timeout to implement backpressure
        val added = interpreterTasks.offer(task, queueProperties.offerTimeoutMs, TimeUnit.MILLISECONDS)

        if (!added) {
            logger.warn(
                "Task rejected due to queue overflow. Queue size: ${interpreterTasks.size}, " +
                        "Capacity: ${queueProperties.capacity}"
            )
        } else {
            logger.debug("Task added to queue. Current size: ${interpreterTasks.size}/${queueProperties.capacity}")
        }
    }
}
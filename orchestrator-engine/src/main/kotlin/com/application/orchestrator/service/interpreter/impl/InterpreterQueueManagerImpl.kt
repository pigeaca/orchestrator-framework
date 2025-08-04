package com.application.orchestrator.service.interpreter.impl

import com.application.orchestrator.service.interpreter.InterpreterQueueManager
import com.application.orchestrator.util.ValidationUtils
import com.orchestrator.proto.InterpreterWorkerTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * Implementation of InterpreterQueueManager.
 * Manages interpreter worker tasks in a queue.
 */
@Service
class InterpreterQueueManagerImpl : InterpreterQueueManager {
    private val logger = LoggerFactory.getLogger(InterpreterQueueManagerImpl::class.java)
    private val interpreterTasks = ArrayDeque<InterpreterWorkerTask>()

    /**
     * Polls for all available interpreter tasks.
     *
     * @return A list of interpreter worker tasks
     */
    override suspend fun pollTasks(): List<InterpreterWorkerTask> {
        val tasks = interpreterTasks.toList()
        interpreterTasks.clear()
        logger.debug("Polled ${tasks.size} interpreter tasks")
        return tasks
    }

    /**
     * Submits an interpreter task to the queue.
     *
     * @param task The interpreter worker task to submit
     * @throws IllegalArgumentException if task is null
     */
    override suspend fun submitTask(task: InterpreterWorkerTask) {
        ValidationUtils.validateNotNull(task, "task")
        logger.debug("Submitting interpreter task: $task")
        interpreterTasks.push(task)
    }
}
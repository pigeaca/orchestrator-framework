package com.application.orchestrator.service.interpreter.impl

import com.application.orchestrator.service.interpreter.InterpreterQueueManager
import com.orchestrator.proto.InterpreterWorkerTask
import org.springframework.stereotype.Service
import java.util.*

@Service
class InterpreterQueueManagerImpl : InterpreterQueueManager {
    private val interpreterTasks = ArrayDeque<InterpreterWorkerTask>()

    override suspend fun pollTasks(): List<InterpreterWorkerTask> {
        val tasks = interpreterTasks.toList()
        interpreterTasks.clear()
        return tasks
    }

    override suspend fun submitTask(task: InterpreterWorkerTask) {
        interpreterTasks.push(task)
    }
}
package com.application.orchestrator.service.interpreter

import com.orchestrator.proto.InterpreterWorkerTask

interface InterpreterQueueManager {
    suspend fun pollTasks(): List<InterpreterWorkerTask>
    suspend fun submitTask(task: InterpreterWorkerTask)
}
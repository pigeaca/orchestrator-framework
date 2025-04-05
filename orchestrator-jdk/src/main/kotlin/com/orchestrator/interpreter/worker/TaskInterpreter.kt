package com.orchestrator.interpreter.worker

import com.orchestrator.interpreter.dsl.OrchestrationDefinition
import com.orchestrator.proto.InterpreterWorkerResult
import com.orchestrator.proto.InterpreterWorkerTask

interface TaskInterpreter {
    suspend fun interpreterTasks(
        interpreterWorkerTasks: List<InterpreterWorkerTask>,
        orchestrationDefinitionsGroup: Map<String, OrchestrationDefinition<*, *>>
    ) : List<InterpreterWorkerResult>
}
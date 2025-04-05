package com.orchestrator.interpreter.worker

import com.orchestrator.interpreter.dsl.OrchestrationDefinition

interface InterpreterWorker {
    fun startInterpreter(orchestrationDefinitions: List<OrchestrationDefinition<*, *>>)
    fun stopInterpreter()
}
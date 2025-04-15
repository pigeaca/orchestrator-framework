package com.orchestrator.interpreter.service
import com.orchestrator.interpreter.dsl.OrchestrationDefinition

interface OrchestratorDefinitionProvider {
    fun definition(): OrchestrationDefinition<*, *>
}
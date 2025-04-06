package com.application.orchestrator.data

import com.application.orchestrator.engine.WorkflowInstance
import com.google.protobuf.ByteString
import java.util.function.BiFunction

interface OrchestratorWorkflowData {
    fun createSagaInstance(workflowId: String, sagaName: String, request: ByteString)
    suspend fun updateSagaIfPresent(workflowId: String, updateFunction: BiFunction<String, WorkflowInstance, WorkflowInstance>): WorkflowInstance?

    suspend fun pollWorkflowRequest(workflowId: String): ByteString?
    suspend fun pollWorkflowResponse(workflowId: String): ByteString?
}
package com.application.orchestrator.data

import com.application.orchestrator.engine.SagaInstance
import com.google.protobuf.ByteString
import java.util.function.BiFunction

interface OrchestratorSagaData {
    fun createSagaInstance(sagaId: String, sagaName: String, request: ByteString)
    suspend fun updateSagaIfPresent(sagaId: String, updateFunction: BiFunction<String, SagaInstance, SagaInstance>): SagaInstance?

    suspend fun pollWorkflowRequest(sagaId: String): ByteString?
    suspend fun pollWorkflowResponse(sagaId: String): ByteString?
}
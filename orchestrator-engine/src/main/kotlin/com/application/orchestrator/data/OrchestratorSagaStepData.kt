package com.application.orchestrator.data

import com.google.protobuf.ByteString

interface OrchestratorSagaStepData {
    suspend fun pollStepData(sagaId: String, stepId: String, stepName: String): ByteString?
}
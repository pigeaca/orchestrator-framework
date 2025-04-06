package com.application.orchestrator.data

import com.google.protobuf.ByteString

interface OrchestratorActivityResultData {
    suspend fun updateActivityResul(sagaId: String, stepType: String, data: ByteString)
    suspend fun getActivityResult(sagaId: String, stepType: String): ByteString?
}
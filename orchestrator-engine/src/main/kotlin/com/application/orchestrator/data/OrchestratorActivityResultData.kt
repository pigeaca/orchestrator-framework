package com.application.orchestrator.data

import com.google.protobuf.ByteString

interface OrchestratorActivityResultData {
    fun initActivityResult(sagaId: String)
    suspend fun updateActivityResul(sagaId: String, stepType: String, data: ByteString): Boolean
    suspend fun getActivityResult(sagaId: String, stepType: String): ByteString?
}
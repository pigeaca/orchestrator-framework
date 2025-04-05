package com.application.orchestrator.data.impl

import com.application.orchestrator.data.OrchestratorActivityResultData
import com.google.protobuf.ByteString
import org.springframework.stereotype.Service

@Service
class OrchestratorActivityResultDataImpl : OrchestratorActivityResultData {
    private val activityResults: MutableMap<String, MutableMap<String, ByteString>> = mutableMapOf()

    override fun initActivityResult(sagaId: String) {
        activityResults[sagaId] = mutableMapOf()
    }

    override suspend fun updateActivityResul(sagaId: String, stepType: String, data: ByteString): Boolean {
        val activityResults = activityResults[sagaId] ?: return false
        activityResults[stepType] = data
        return true
    }

    override suspend fun getActivityResult(sagaId: String, stepType: String): ByteString? {
        val activityResults = activityResults[sagaId] ?: return null
        return activityResults[stepType]
    }

}
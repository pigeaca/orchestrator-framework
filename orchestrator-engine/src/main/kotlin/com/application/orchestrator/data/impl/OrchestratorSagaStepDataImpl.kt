package com.application.orchestrator.data.impl

import com.application.orchestrator.data.OrchestratorSagaStepData
import com.google.protobuf.ByteString
import org.springframework.stereotype.Service

@Service
class OrchestratorSagaStepDataImpl : OrchestratorSagaStepData {
    private val activityResults: MutableMap<String, MutableMap<String, ByteString>> = mutableMapOf()

    override suspend fun pollStepData(sagaId: String, stepId: String, stepName: String): ByteString? {
        return activityResults[sagaId]?.get(stepName)
    }
}
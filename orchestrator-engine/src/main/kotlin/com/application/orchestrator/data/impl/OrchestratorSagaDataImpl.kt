package com.application.orchestrator.data.impl

import com.application.orchestrator.data.OrchestratorSagaData
import com.application.orchestrator.engine.SagaInstance
import com.application.orchestrator.engine.SagaStatus
import com.google.protobuf.ByteString
import org.springframework.stereotype.Service
import java.util.function.BiFunction

@Service
class OrchestratorSagaDataImpl : OrchestratorSagaData {
    private val sagaInstances = mutableMapOf<String, SagaInstance>()

    override fun createSagaInstance(sagaId: String, sagaName: String, request: ByteString) {
        sagaInstances.computeIfAbsent(sagaId) {
            SagaInstance(
                sagaId = sagaId,
                sagaName = sagaName,
                request = request,
                sagaStatus = SagaStatus.IN_PROGRESS,
                response = null
            )
        }
    }

    override suspend fun updateSagaIfPresent(sagaId: String, updateFunction: BiFunction<String, SagaInstance, SagaInstance>): SagaInstance? {
        return sagaInstances.computeIfPresent(sagaId, updateFunction)
    }

    override suspend fun pollWorkflowRequest(sagaId: String): ByteString? {
        return sagaInstances[sagaId]?.request
    }

    override suspend fun pollWorkflowResponse(sagaId: String): ByteString? {
        return sagaInstances[sagaId]?.response
    }
}
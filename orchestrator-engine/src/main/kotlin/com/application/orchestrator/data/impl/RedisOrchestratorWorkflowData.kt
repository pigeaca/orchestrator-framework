package com.application.orchestrator.data.impl

import com.application.orchestrator.data.OrchestratorWorkflowData
import com.application.orchestrator.engine.WorkflowInstance
import com.application.orchestrator.engine.WorkflowStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.ByteString
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import java.util.function.BiFunction

@Service
class RedisOrchestratorWorkflowData(
    private val redisTemplate: ReactiveRedisTemplate<String, ByteArray>,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : OrchestratorWorkflowData {

    override fun createSagaInstance(workflowId: String, sagaName: String, request: ByteString) {
        val key = keyFor(workflowId)
        val instance = WorkflowInstance(
            workflowId = workflowId,
            workflowName = sagaName,
            request = request.toByteArray(),
            response = null,
            workflowStatus = WorkflowStatus.IN_PROGRESS
        )
        val value = objectMapper.writeValueAsBytes(instance)
        redisTemplate.opsForValue().set(key, value).subscribe()
    }

    override suspend fun updateSagaIfPresent(
        workflowId: String,
        updateFunction: BiFunction<String, WorkflowInstance, WorkflowInstance>
    ): WorkflowInstance? {
        val key = keyFor(workflowId)
        val current = redisTemplate.opsForValue().get(key).awaitFirstOrNull() ?: return null
        val existing = objectMapper.readValue(current, WorkflowInstance::class.java)
        val updated = updateFunction.apply(workflowId, existing)
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsBytes(updated)).awaitFirstOrNull()
        return updated
    }

    override suspend fun pollWorkflowRequest(workflowId: String): ByteString? {
        val instance = load(workflowId) ?: return null
        return ByteString.copyFrom(instance.request)
    }

    override suspend fun pollWorkflowResponse(workflowId: String): ByteString? {
        val instance = load(workflowId) ?: return null
        instance.response ?: return null
        return ByteString.copyFrom(instance.response)
    }

    private suspend fun load(id: String): WorkflowInstance? {
        val key = keyFor(id)
        val bytes = redisTemplate.opsForValue().get(key).awaitFirstOrNull() ?: return null
        return objectMapper.readValue(bytes, WorkflowInstance::class.java)
    }

    private fun keyFor(workflowId: String) = "workflow_instance:$workflowId"
}
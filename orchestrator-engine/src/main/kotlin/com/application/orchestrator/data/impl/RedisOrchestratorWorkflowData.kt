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

/**
 * Redis implementation of OrchestratorWorkflowData.
 * Manages workflow instances in Redis.
 */
@Service
class RedisOrchestratorWorkflowData(
    redisTemplate: ReactiveRedisTemplate<String, ByteArray>,
    objectMapper: ObjectMapper = jacksonObjectMapper()
) : BaseRedisRepository<WorkflowInstance>(redisTemplate, objectMapper, WorkflowInstance::class.java), OrchestratorWorkflowData {

    /**
     * Creates a new workflow instance.
     *
     * @param workflowId The ID of the workflow
     * @param workflowName The name of the workflow
     * @param request The request data for the workflow
     */
    override fun createWorkflowInstance(workflowId: String, workflowName: String, request: ByteString) {
        val key = keyFor(workflowId)
        val instance = WorkflowInstance(
            workflowId = workflowId,
            workflowName = workflowName,
            request = request.toByteArray(),
            response = null,
            workflowStatus = WorkflowStatus.IN_PROGRESS
        )
        val value = objectMapper.writeValueAsBytes(instance)
        redisTemplate.opsForValue().set(key, value).subscribe()
    }

    /**
     * Updates a workflow instance if it exists.
     *
     * @param workflowId The ID of the workflow
     * @param updateFunction The function to apply to update the workflow instance
     * @return The updated workflow instance, or null if not found
     */
    override suspend fun updateWorkflowIfPresent(
        workflowId: String,
        updateFunction: BiFunction<String, WorkflowInstance, WorkflowInstance>
    ): WorkflowInstance? {
        val key = keyFor(workflowId)
        val existing = load(key) ?: return null
        val updated = updateFunction.apply(workflowId, existing)
        save(key, updated)
        return updated
    }

    override suspend fun pollWorkflowRequest(workflowId: String): ByteString? {
        val instance = loadWorkflow(workflowId) ?: return null
        return ByteString.copyFrom(instance.request)
    }

    override suspend fun pollWorkflowResponse(workflowId: String): ByteString? {
        val instance = loadWorkflow(workflowId) ?: return null
        instance.response ?: return null
        return ByteString.copyFrom(instance.response)
    }

    private suspend fun loadWorkflow(id: String): WorkflowInstance? {
        val key = keyFor(id)
        return load(key)
    }

    private fun keyFor(workflowId: String) = "workflow_instance:$workflowId"
}
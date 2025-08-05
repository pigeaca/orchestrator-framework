package com.application.orchestrator.data.impl

import com.application.orchestrator.config.CacheConfig.Companion.WORKFLOW_CACHE
import com.application.orchestrator.data.OrchestratorWorkflowData
import com.application.orchestrator.engine.WorkflowInstance
import com.application.orchestrator.engine.WorkflowStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.ByteString
import com.orchestrator.util.ByteStringUtil
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import java.util.function.BiFunction

/**
 * Redis implementation of OrchestratorWorkflowData with caching support.
 * Manages workflow instances in Redis with an in-memory cache for frequently accessed workflows.
 */
@Service
open class RedisOrchestratorWorkflowData(
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
     * Evicts the cache entry for the updated workflow.
     *
     * @param workflowId The ID of the workflow
     * @param updateFunction The function to apply to update the workflow instance
     * @return The updated workflow instance, or null if not found
     */
    @CacheEvict(value = [WORKFLOW_CACHE], key = "#workflowId")
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

    /**
     * Loads a workflow request from cache or Redis.
     * Uses the workflow ID as the cache key.
     *
     * @param workflowId The workflow ID
     * @return The workflow request, or null if not found
     */
    @Cacheable(value = [WORKFLOW_CACHE], key = "#workflowId", unless = "#result == null")
    override suspend fun pollWorkflowRequest(workflowId: String): ByteString? {
        val instance = loadWorkflow(workflowId) ?: return null
        return ByteStringUtil.wrapBytes(instance.request)
    }

    /**
     * Loads a workflow result from cache or Redis.
     * Uses the workflow ID as the cache key.
     *
     * @param workflowId The workflow ID
     * @return The workflow result, or null if not found
     */
    @Cacheable(value = [WORKFLOW_CACHE], key = "#workflowId", unless = "#result == null")
    override suspend fun pollWorkflowResponse(workflowId: String): ByteString? {
        val instance = loadWorkflow(workflowId) ?: return null
        instance.response ?: return null
        return ByteStringUtil.wrapBytes(instance.response)
    }

    protected open suspend fun loadWorkflow(id: String): WorkflowInstance? {
        val key = keyFor(id)
        return load(key)
    }

    private fun keyFor(workflowId: String) = "workflow_instance:$workflowId"
}
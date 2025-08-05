package com.application.orchestrator.data.impl

import com.application.orchestrator.config.CacheConfig.Companion.SAGA_STEP_CACHE
import com.application.orchestrator.data.OrchestratorWorkflowStepData
import com.application.orchestrator.engine.StepStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Redis implementation of OrchestratorWorkflowStepData with caching support.
 * Manages workflow steps in Redis with an in-memory cache for frequently accessed steps.
 */
@Service
open class RedisWorkflowStepRepository(
    redisTemplate: ReactiveRedisTemplate<String, ByteArray>,
    objectMapper: ObjectMapper = jacksonObjectMapper()
) : BaseRedisRepository<WorkflowStep>(redisTemplate, objectMapper, WorkflowStep::class.java), OrchestratorWorkflowStepData {

    @CacheEvict(value = [SAGA_STEP_CACHE], key = "'step:' + #workflowId + ':' + #step.stepId")
    override suspend fun saveStep(workflowId: String, step: WorkflowStep) {
        val key = stepKey(workflowId, step.stepId)
        save(key, step)
    }

    @Cacheable(value = [SAGA_STEP_CACHE], key = "'allSteps:' + #workflowId")
    override suspend fun loadAllSteps(workflowId: String): List<WorkflowStep> {
        val pattern = stepKey(workflowId, "*")
        return scanAndLoad(pattern)
    }

    @Caching(evict = [
        CacheEvict(value = [SAGA_STEP_CACHE], key = "'step:' + #workflowId + ':' + #stepId"),
        CacheEvict(value = [SAGA_STEP_CACHE], key = "'allSteps:' + #workflowId")
    ])
    override suspend fun updateStepStatus(workflowId: String, stepId: String, status: StepStatus) {
        val step = loadStep(workflowId, stepId) ?: return
        val updated = step.copy(status = status, updatedAt = Instant.now())
        saveStep(workflowId, updated)
    }

    @Cacheable(value = [SAGA_STEP_CACHE], key = "'stepsByStatus:' + #workflowId + ':' + #status")
    override suspend fun findStepsByStatus(workflowId: String, status: StepStatus): List<WorkflowStep> {
        return loadAllSteps(workflowId).filter { it.status == status }
    }

    override suspend fun findStepsTimedOut(now: Instant): List<WorkflowStep> {
        return loadAllStepsForAllWorkflows()
            .filter { it.status == StepStatus.IN_PROGRESS && now.isAfter(it.timeoutDeadline()) }
    }

    @Cacheable(value = [SAGA_STEP_CACHE], key = "'step:' + #workflowId + ':' + #stepId", unless = "#result == null")
    protected open suspend fun loadStep(workflowId: String, stepId: String): WorkflowStep? {
        val key = stepKey(workflowId, stepId)
        return load(key)
    }

    protected open suspend fun loadAllStepsForAllWorkflows(): List<WorkflowStep> {
        val pattern = "step:*:*"
        return scanAndLoad(pattern)
    }

    private fun WorkflowStep.timeoutDeadline(): Instant =
        updatedAt?.plus(timeout) ?: Instant.EPOCH

    private fun stepKey(workflowId: String, stepId: String) = "step:$workflowId:$stepId"
}

data class WorkflowStep(
    val stepId: String,
    val workflowId: String,
    val type: String,
    val status: StepStatus,
    val queue: String,
    val timeout: Duration,
    val updatedAt: Instant? = null,
    val maxAttempts: Int = 3
)
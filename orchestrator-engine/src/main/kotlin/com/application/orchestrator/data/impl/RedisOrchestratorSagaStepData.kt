package com.application.orchestrator.data.impl

import com.application.orchestrator.data.OrchestratorSagaStepData
import com.application.orchestrator.engine.StepStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class RedisSagaStepRepository(
    private val redis: ReactiveRedisTemplate<String, ByteArray>,
    private val mapper: ObjectMapper = jacksonObjectMapper()
) : OrchestratorSagaStepData {

    override suspend fun saveStep(sagaId: String, step: WorkflowStep) {
        val key = stepKey(sagaId, step.stepId)
        val bytes = mapper.writeValueAsBytes(step)
        redis.opsForValue().set(key, bytes).awaitFirstOrNull()
    }

    override suspend fun loadAllSteps(sagaId: String): List<WorkflowStep> {
        val pattern = stepKey(sagaId, "*")
        return redis.scan(ScanOptions.scanOptions().match(pattern).build())
            .flatMap { key -> redis.opsForValue().get(key) }
            .map { mapper.readValue(it, WorkflowStep::class.java) }
            .collectList()
            .awaitFirstOrNull() ?: emptyList()
    }

    override suspend fun updateStepStatus(sagaId: String, stepId: String, status: StepStatus) {
        val step = loadStep(sagaId, stepId) ?: return
        val updated = step.copy(status = status, updatedAt = Instant.now())
        saveStep(sagaId, updated)
    }

    override suspend fun findStepsByStatus(sagaId: String, status: StepStatus): List<WorkflowStep> {
        return loadAllSteps(sagaId).filter { it.status == status }
    }

    override suspend fun findStepsTimedOut(now: Instant): List<WorkflowStep> {
        return loadAllStepsForAllSagas()
            .filter { it.status == StepStatus.IN_PROGRESS && now.isAfter(it.timeoutDeadline()) }
    }

    private suspend fun loadStep(sagaId: String, stepId: String): WorkflowStep? {
        val key = stepKey(sagaId, stepId)
        val bytes = redis.opsForValue().get(key).awaitFirstOrNull() ?: return null
        return mapper.readValue(bytes, WorkflowStep::class.java)
    }

    private suspend fun loadAllStepsForAllSagas(): List<WorkflowStep> {
        val pattern = "step:*:*"
        return redis.scan(ScanOptions.scanOptions().match(pattern).build())
            .flatMap { key -> redis.opsForValue().get(key) }
            .map { mapper.readValue(it, WorkflowStep::class.java) }
            .collectList()
            .awaitFirstOrNull() ?: emptyList()
    }

    private fun WorkflowStep.timeoutDeadline(): Instant =
        updatedAt?.plus(timeout) ?: Instant.EPOCH

    private fun stepKey(sagaId: String, stepId: String) = "step:$sagaId:$stepId"
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
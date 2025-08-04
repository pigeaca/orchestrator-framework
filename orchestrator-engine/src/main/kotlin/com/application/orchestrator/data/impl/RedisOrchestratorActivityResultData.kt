package com.application.orchestrator.data.impl

import com.application.orchestrator.data.OrchestratorActivityResultData
import com.google.protobuf.ByteString
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service

/**
 * Redis implementation of OrchestratorActivityResultData.
 * Stores and retrieves activity results using Redis hash operations.
 */
@Service
class RedisOrchestratorActivityResultData(
    redisTemplate: ReactiveRedisTemplate<String, ByteArray>
) : BaseRedisRepository<ByteArray>(redisTemplate, entityClass = ByteArray::class.java), OrchestratorActivityResultData {

    override suspend fun updateActivityResul(sagaId: String, stepType: String, data: ByteString) {
        val key = keyFor(sagaId)
        saveToHash(key, stepType, data.toByteArray())
    }

    override suspend fun getActivityResult(sagaId: String, stepType: String): ByteString? {
        val key = keyFor(sagaId)
        return loadFromHash(key, stepType)
    }

    private fun keyFor(sagaId: String) = "activity_result:$sagaId"
}
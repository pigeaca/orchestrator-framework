package com.application.orchestrator.data.impl

import com.application.orchestrator.config.CacheConfig.Companion.ACTIVITY_RESULT_CACHE
import com.application.orchestrator.data.OrchestratorActivityResultData
import com.google.protobuf.ByteString
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service

/**
 * Redis implementation of OrchestratorActivityResultData with caching support.
 * Stores and retrieves activity results using Redis hash operations with an in-memory cache
 * for frequently accessed results.
 */
@Service
open class RedisOrchestratorActivityResultData(
    redisTemplate: ReactiveRedisTemplate<String, ByteArray>
) : BaseRedisRepository<ByteArray>(redisTemplate, entityClass = ByteArray::class.java), OrchestratorActivityResultData {

    @CacheEvict(value = [ACTIVITY_RESULT_CACHE], key = "#sagaId + ':' + #stepType")
    override suspend fun updateActivityResul(sagaId: String, stepType: String, data: ByteString) {
        val key = keyFor(sagaId)
        saveToHash(key, stepType, data.toByteArray())
    }

    @Cacheable(value = [ACTIVITY_RESULT_CACHE], key = "#sagaId + ':' + #stepType", unless = "#result == null")
    override suspend fun getActivityResult(sagaId: String, stepType: String): ByteString? {
        val key = keyFor(sagaId)
        return loadFromHash(key, stepType)
    }

    private fun keyFor(sagaId: String) = "activity_result:$sagaId"
}
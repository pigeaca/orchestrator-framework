package com.application.orchestrator.data.impl

import com.application.orchestrator.data.OrchestratorActivityResultData
import com.google.protobuf.ByteString
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service

@Service
class RedisOrchestratorActivityResultData(
    private val redisTemplate: ReactiveRedisTemplate<String, ByteArray>
) : OrchestratorActivityResultData {

    override suspend fun updateActivityResul(sagaId: String, stepType: String, data: ByteString) {
        val key = keyFor(sagaId)
        redisTemplate.opsForHash<String, ByteArray>()
            .put(key, stepType, data.toByteArray())
            .awaitFirstOrNull()
    }

    override suspend fun getActivityResult(sagaId: String, stepType: String): ByteString? {
        val key = keyFor(sagaId)
        return redisTemplate.opsForHash<String, ByteArray>()
            .get(key, stepType)
            .map { ByteString.copyFrom(it) }
            .awaitFirstOrNull()
    }

    private fun keyFor(sagaId: String) = "activity_result:$sagaId"

}
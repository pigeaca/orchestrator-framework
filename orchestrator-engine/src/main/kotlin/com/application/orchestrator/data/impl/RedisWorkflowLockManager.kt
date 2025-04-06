package com.application.orchestrator.data.impl

import com.application.orchestrator.data.WorkflowLockManager
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RedisWorkflowLockManager(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
): WorkflowLockManager {

    override suspend fun tryAcquire(workflowId: String, ownerId: String, ttl: Duration): Boolean {
        val ops = redisTemplate.opsForValue()
        return ops.setIfAbsent(lockKey(workflowId), ownerId, ttl).awaitFirstOrNull() == true
    }

    override suspend fun release(workflowId: String, ownerId: String) {
        val current = redisTemplate.opsForValue().get(lockKey(workflowId)).awaitFirstOrNull()
        if (current == ownerId) {
            redisTemplate.delete(lockKey(workflowId)).awaitFirstOrNull()
        }
    }

    private fun lockKey(workflowId: String) = "lock:saga:$workflowId"
}
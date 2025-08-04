package com.application.orchestrator.data.impl

import com.application.orchestrator.data.WorkflowLockManager
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Redis implementation of WorkflowLockManager.
 * Provides distributed locking mechanism for workflow operations.
 * 
 * Note: This implementation uses a String-based Redis template rather than
 * extending BaseRedisRepository because it has different requirements:
 * - It stores simple string values rather than serialized objects
 * - It requires specialized Redis operations like setIfAbsent with TTL
 * - It doesn't need the serialization/deserialization functionality
 */
@Service
class RedisWorkflowLockManager(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
): WorkflowLockManager {

    /**
     * Attempts to acquire a lock for the specified workflow.
     *
     * @param workflowId The ID of the workflow to lock
     * @param ownerId The ID of the lock owner (used to ensure only the owner can release)
     * @param ttl The time-to-live for the lock
     * @return true if the lock was acquired, false otherwise
     */
    override suspend fun tryAcquire(workflowId: String, ownerId: String, ttl: Duration): Boolean {
        val ops = redisTemplate.opsForValue()
        return ops.setIfAbsent(lockKey(workflowId), ownerId, ttl).awaitFirstOrNull() == true
    }

    /**
     * Releases a lock for the specified workflow if the current owner matches.
     *
     * @param workflowId The ID of the workflow to unlock
     * @param ownerId The ID of the lock owner (only the owner can release the lock)
     */
    override suspend fun release(workflowId: String, ownerId: String) {
        val current = redisTemplate.opsForValue().get(lockKey(workflowId)).awaitFirstOrNull()
        if (current == ownerId) {
            redisTemplate.delete(lockKey(workflowId)).awaitFirstOrNull()
        }
    }

    /**
     * Generates a Redis key for the workflow lock.
     *
     * @param workflowId The ID of the workflow
     * @return The Redis key for the lock
     */
    private fun lockKey(workflowId: String) = "lock:workflow:$workflowId"
}
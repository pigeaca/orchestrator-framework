package com.application.orchestrator.data.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.ByteString
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ScanOptions

/**
 * Base class for Redis repositories to eliminate code duplication across data access implementations.
 * Provides common functionality for Redis operations, serialization/deserialization, and key generation.
 */
abstract class BaseRedisRepository<T : Any>(
    protected val redisTemplate: ReactiveRedisTemplate<String, ByteArray>,
    protected val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val entityClass: Class<T>
) {
    /**
     * Saves an entity to Redis.
     *
     * @param key The Redis key
     * @param entity The entity to save
     */
    protected suspend fun save(key: String, entity: T) {
        val bytes = objectMapper.writeValueAsBytes(entity)
        redisTemplate.opsForValue().set(key, bytes).awaitFirstOrNull()
    }

    /**
     * Loads an entity from Redis.
     *
     * @param key The Redis key
     * @return The loaded entity or null if not found
     */
    protected suspend fun load(key: String): T? {
        val bytes = redisTemplate.opsForValue().get(key).awaitFirstOrNull() ?: return null
        return objectMapper.readValue(bytes, entityClass)
    }

    /**
     * Scans Redis for keys matching a pattern and loads all matching entities.
     *
     * @param pattern The key pattern to match
     * @return List of loaded entities
     */
    protected suspend fun scanAndLoad(pattern: String): List<T> {
        return redisTemplate.scan(ScanOptions.scanOptions().match(pattern).build())
            .flatMap { key -> redisTemplate.opsForValue().get(key) }
            .map { objectMapper.readValue(it, entityClass) }
            .collectList()
            .awaitFirstOrNull() ?: emptyList()
    }

    /**
     * Saves a value to a Redis hash.
     *
     * @param key The Redis key
     * @param field The hash field
     * @param value The value to save
     */
    protected suspend fun saveToHash(key: String, field: String, value: ByteArray) {
        redisTemplate.opsForHash<String, ByteArray>()
            .put(key, field, value)
            .awaitFirstOrNull()
    }

    /**
     * Loads a value from a Redis hash.
     *
     * @param key The Redis key
     * @param field The hash field
     * @return The loaded value as ByteString or null if not found
     */
    protected suspend fun loadFromHash(key: String, field: String): ByteString? {
        return redisTemplate.opsForHash<String, ByteArray>()
            .get(key, field)
            .map { ByteString.copyFrom(it) }
            .awaitFirstOrNull()
    }

    /**
     * Converts a ByteString to a ByteArray.
     *
     * @param byteString The ByteString to convert
     * @return The resulting ByteArray
     */
    protected fun byteStringToByteArray(byteString: ByteString): ByteArray {
        return byteString.toByteArray()
    }

    /**
     * Converts a ByteArray to a ByteString.
     *
     * @param byteArray The ByteArray to convert
     * @return The resulting ByteString
     */
    protected fun byteArrayToByteString(byteArray: ByteArray): ByteString {
        return ByteString.copyFrom(byteArray)
    }
}
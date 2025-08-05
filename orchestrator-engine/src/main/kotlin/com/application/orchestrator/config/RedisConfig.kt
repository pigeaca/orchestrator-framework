package com.application.orchestrator.config

import io.lettuce.core.ClientOptions
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis configuration with connection pooling support.
 * Uses Lettuce client with connection pooling for better performance under high load.
 */
@Configuration
open class RedisConfig(private val redisProperties: RedisProperties) {

    @Bean
    open fun clientResources(): ClientResources {
        return DefaultClientResources.builder().build()
    }

    @Bean
    open fun reactiveRedisConnectionFactory(clientResources: ClientResources): ReactiveRedisConnectionFactory {
        val standaloneConfig = RedisStandaloneConfiguration().apply {
            hostName = redisProperties.host
            port = redisProperties.port
            redisProperties.password?.let { password = RedisPassword.of(it) }
            database = redisProperties.database
        }

        // Configure connection pooling
        val poolConfig = GenericObjectPoolConfig<Any>().apply {
            maxIdle = redisProperties.pool.maxIdle
            minIdle = redisProperties.pool.minIdle
            maxTotal = redisProperties.pool.maxActive
            maxWaitMillis = redisProperties.pool.maxWait
            timeBetweenEvictionRunsMillis = redisProperties.pool.timeBetweenEvictionRuns
        }

        val clientOptions = ClientOptions.builder()
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .autoReconnect(true)
            .build()

        val clientConfig = LettucePoolingClientConfiguration.builder()
            .clientOptions(clientOptions)
            .clientResources(clientResources)
            .commandTimeout(Duration.ofMillis(redisProperties.timeout))
            .poolConfig(poolConfig)
            .build()

        return LettuceConnectionFactory(standaloneConfig, clientConfig)
    }

    @Bean
    open fun byteArrayRedisTemplate(factory: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, ByteArray> {
        val keySerializer = StringRedisSerializer()
        val valueSerializer = RawByteArrayRedisSerializer()

        val context = RedisSerializationContext
            .newSerializationContext<String, ByteArray>(keySerializer)
            .value(valueSerializer)
            .hashKey(keySerializer)
            .hashValue(valueSerializer)
            .build()

        return ReactiveRedisTemplate(factory, context)
    }
}

class RawByteArrayRedisSerializer : RedisSerializer<ByteArray> {

    override fun serialize(t: ByteArray?): ByteArray {
        return t ?: ByteArray(0)
    }

    override fun deserialize(bytes: ByteArray?): ByteArray? {
        return bytes
    }
}
package com.application.orchestrator.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
open class RedisConfig {

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
package com.application.orchestrator.config

import com.application.orchestrator.util.CircuitBreaker
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for circuit breakers.
 */
@ConfigurationProperties(prefix = "orchestrator.circuit-breaker")
data class CircuitBreakerProperties(
    val enabled: Boolean = true,
    val failureThreshold: Int = 5,
    val resetTimeoutMs: Long = 30000,
    val halfOpenMaxCalls: Int = 3
)

/**
 * Configuration for circuit breakers.
 * Creates and configures circuit breaker instances for different services.
 */
@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties::class)
open class CircuitBreakerConfig(
    private val properties: CircuitBreakerProperties
) {
    /**
     * Creates a circuit breaker for the activity service gRPC calls.
     *
     * @return The circuit breaker instance
     */
    @Bean
    open fun activityServiceCircuitBreaker(): CircuitBreaker {
        return CircuitBreaker(
            name = "activity-service-grpc",
            failureThreshold = properties.failureThreshold,
            resetTimeout = properties.resetTimeoutMs,
            halfOpenMaxCalls = properties.halfOpenMaxCalls
        )
    }

    /**
     * Creates a circuit breaker for the interpreter service gRPC calls.
     *
     * @return The circuit breaker instance
     */
    @Bean
    open fun interpreterServiceCircuitBreaker(): CircuitBreaker {
        return CircuitBreaker(
            name = "interpreter-service-grpc",
            failureThreshold = properties.failureThreshold,
            resetTimeout = properties.resetTimeoutMs,
            halfOpenMaxCalls = properties.halfOpenMaxCalls
        )
    }

    /**
     * Creates a circuit breaker for Redis operations.
     *
     * @return The circuit breaker instance
     */
    @Bean
    open fun redisCircuitBreaker(): CircuitBreaker {
        return CircuitBreaker(
            name = "redis-operations",
            failureThreshold = properties.failureThreshold,
            resetTimeout = properties.resetTimeoutMs,
            halfOpenMaxCalls = properties.halfOpenMaxCalls
        )
    }
}
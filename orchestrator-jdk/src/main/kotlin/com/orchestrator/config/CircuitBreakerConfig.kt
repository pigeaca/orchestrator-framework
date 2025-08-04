package com.orchestrator.config

import com.orchestrator.util.CircuitBreaker
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
     * Creates a circuit breaker for the workflow engine gRPC service.
     *
     * @return The circuit breaker instance
     */
    @Bean
    open fun workflowEngineCircuitBreaker(): CircuitBreaker {
        return CircuitBreaker(
            name = "workflow-engine-grpc",
            failureThreshold = properties.failureThreshold,
            resetTimeout = properties.resetTimeoutMs,
            halfOpenMaxCalls = properties.halfOpenMaxCalls
        )
    }

    /**
     * Creates a circuit breaker for the interpreter worker gRPC service.
     *
     * @return The circuit breaker instance
     */
    @Bean
    open fun interpreterWorkerCircuitBreaker(): CircuitBreaker {
        return CircuitBreaker(
            name = "interpreter-worker-grpc",
            failureThreshold = properties.failureThreshold,
            resetTimeout = properties.resetTimeoutMs,
            halfOpenMaxCalls = properties.halfOpenMaxCalls
        )
    }
}
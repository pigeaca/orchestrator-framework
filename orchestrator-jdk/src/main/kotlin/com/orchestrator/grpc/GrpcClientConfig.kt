package com.orchestrator.grpc

import com.orchestrator.config.WorkflowEngineProperties
import com.orchestrator.util.CircuitBreaker
import io.grpc.Channel
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit


@Configuration
open class GrpcClientConfig : DisposableBean {
    private val logger = LoggerFactory.getLogger(GrpcClientConfig::class.java)
    private var managedChannel: ManagedChannel? = null

    /**
     * Creates a ManagedChannel for the workflow engine gRPC service.
     * The channel is configured with a circuit breaker to prevent cascading failures.
     *
     * @param workflowEngineProperties The properties for connecting to the workflow engine
     * @return The managed channel
     */
    @Bean(value = ["engine"])
    open fun grpcManagedChannel(
        workflowEngineProperties: WorkflowEngineProperties
    ): Channel {
        // Create the base channel
        val baseChannel = ManagedChannelBuilder.forAddress(workflowEngineProperties.host, workflowEngineProperties.port)
            .usePlaintext()
            // Add any additional channel configuration here
            // For example, we could add retry settings, timeouts, etc.
            .build()
        
        // Create a simple circuit breaker
        val circuitBreaker = CircuitBreaker(
            name = "workflow-engine-grpc",
            failureThreshold = 5,
            resetTimeout = 30000,
            halfOpenMaxCalls = 3
        )
        
        // Create a circuit breaker interceptor and apply it to the channel
        val circuitBreakerInterceptor = CircuitBreakerInterceptor(circuitBreaker)
        
        // Apply the interceptor to the channel
        // This creates a new channel that uses the interceptor
        val channelWithInterceptor = ClientInterceptors.intercept(baseChannel, circuitBreakerInterceptor)

        // Store reference to base channel for proper cleanup
        this.managedChannel = baseChannel
        
        logger.info("Created gRPC channel with circuit breaker to ${workflowEngineProperties.host}:${workflowEngineProperties.port}")
        
        // Return the wrapped channel that uses the circuit breaker interceptor
        return channelWithInterceptor
    }
    
    /**
     * Properly shut down the gRPC channel when the Spring context is destroyed.
     * This ensures that all resources are released and there are no connection leaks.
     */
    override fun destroy() {
        logger.info("Shutting down gRPC managed channel")
        managedChannel?.let { channel ->
            try {
                // Try graceful shutdown first
                if (!channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("gRPC channel did not terminate gracefully within timeout, forcing shutdown")
                    channel.shutdownNow()
                    
                    if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.error("gRPC channel did not terminate even after forced shutdown")
                    }
                }
                logger.info("gRPC channel shut down successfully")
            } catch (e: InterruptedException) {
                logger.error("Interrupted while waiting for gRPC channel to shut down", e)
                Thread.currentThread().interrupt()
                channel.shutdownNow()
            }
        }
    }
}
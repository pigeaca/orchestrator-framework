package com.orchestrator.grpc

import com.orchestrator.config.WorkflowEngineProperties
import com.orchestrator.util.CircuitBreaker
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

@Configuration
open class GrpcClientConfig : DisposableBean {
    private val logger = LoggerFactory.getLogger(GrpcClientConfig::class.java)
    private var managedChannel: ManagedChannel? = null

    @Bean(value = ["engine"])
    open fun grpcManagedChannel(
        workflowEngineProperties: WorkflowEngineProperties,
        workflowEngineCircuitBreaker: CircuitBreaker
    ): ManagedChannel {
        // Create the base channel
        val baseChannel = ManagedChannelBuilder.forAddress(workflowEngineProperties.host, workflowEngineProperties.port)
            .usePlaintext()
            .build()
        
        // Create a circuit breaker interceptor
        val circuitBreakerInterceptor = CircuitBreakerInterceptor(workflowEngineCircuitBreaker)
        
        // Create a channel with the circuit breaker interceptor
        val channel = ClientInterceptors.intercept(baseChannel, circuitBreakerInterceptor)
        
        // Store reference to channel for proper cleanup
        this.managedChannel = baseChannel
        
        logger.info("Created gRPC channel with circuit breaker to ${workflowEngineProperties.host}:${workflowEngineProperties.port}")
        
        return channel
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
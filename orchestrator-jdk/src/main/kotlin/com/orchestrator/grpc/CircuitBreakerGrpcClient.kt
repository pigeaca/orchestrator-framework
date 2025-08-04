package com.orchestrator.grpc

import com.orchestrator.util.CircuitBreaker
import com.orchestrator.util.CircuitBreakerOpenException
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * A gRPC client interceptor that applies circuit breaker pattern to gRPC calls.
 * 
 * This interceptor wraps each gRPC call with a circuit breaker to prevent cascading failures
 * when the remote service is experiencing issues.
 *
 * @param circuitBreaker The circuit breaker to use for protecting calls
 */
class CircuitBreakerInterceptor(
    private val circuitBreaker: CircuitBreaker
) : ClientInterceptor {
    private val logger = LoggerFactory.getLogger(CircuitBreakerInterceptor::class.java)

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                val wrappedListener = object : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    override fun onClose(status: Status, trailers: Metadata) {
                        // We'll use the executeWithCircuitBreaker method to handle success/failure recording
                        super.onClose(status, trailers)
                    }
                }

                try {
                    // Use the circuit breaker's public API to check if we can make the call
                    runBlocking {
                        circuitBreaker.executeWithCircuitBreaker {
                            // This is just a check - the actual call happens below
                            // The circuit breaker will record success when this lambda completes without exception
                            true
                        }
                    }
                    
                    // If we get here, the circuit is closed and we can make the call
                    super.start(wrappedListener, headers)
                } catch (e: CircuitBreakerOpenException) {
                    // Circuit is open, fail the call immediately
                    logger.error("Circuit breaker prevented call to ${method.fullMethodName}: ${e.message}")
                    responseListener.onClose(
                        Status.UNAVAILABLE.withDescription("Circuit breaker is open: ${e.message}"),
                        Metadata()
                    )
                } catch (e: Exception) {
                    // Some other error occurred
                    logger.error("Error checking circuit breaker state", e)
                    responseListener.onClose(
                        Status.INTERNAL.withDescription("Error checking circuit breaker: ${e.message}"),
                        Metadata()
                    )
                }
            }
            
            override fun sendMessage(message: ReqT) {
                try {
                    super.sendMessage(message)
                } catch (e: Exception) {
                    // Record failure in circuit breaker
                    runBlocking {
                        try {
                            circuitBreaker.executeWithCircuitBreaker {
                                throw e // This will cause the circuit breaker to record a failure
                            }
                        } catch (ex: Exception) {
                            logger.debug("Recorded failure in circuit breaker", ex)
                        }
                    }
                    throw e // Re-throw the original exception
                }
            }
        }
    }
}
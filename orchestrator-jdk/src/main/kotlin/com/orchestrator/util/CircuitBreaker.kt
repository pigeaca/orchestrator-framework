package com.orchestrator.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Implementation of the Circuit Breaker pattern for protecting against failures in remote service calls.
 * 
 * The circuit breaker has three states:
 * - CLOSED: All requests are allowed through (normal operation)
 * - OPEN: All requests are rejected immediately (service is considered unavailable)
 * - HALF_OPEN: A limited number of test requests are allowed through to check if the service has recovered
 *
 * @param failureThreshold The number of failures that must occur before the circuit opens
 * @param resetTimeout The time in milliseconds after which to try resetting the circuit (enter HALF_OPEN state)
 * @param halfOpenMaxCalls The maximum number of calls to allow in HALF_OPEN state
 */
class CircuitBreaker(
    private val name: String,
    private val failureThreshold: Int = 5,
    private val resetTimeout: Long = 30000, // 30 seconds
    private val halfOpenMaxCalls: Int = 3
) {
    private val logger = LoggerFactory.getLogger(CircuitBreaker::class.java)
    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val lastStateChange = AtomicReference(Instant.now())
    private val halfOpenCallCount = AtomicInteger(0)
    private val mutex = Mutex()

    enum class State {
        CLOSED, OPEN, HALF_OPEN
    }

    /**
     * Executes the given operation with circuit breaker protection.
     *
     * @param operation The operation to execute
     * @return The result of the operation
     * @throws CircuitBreakerOpenException if the circuit is open
     * @throws Exception if the operation fails
     */
    suspend fun <T> executeWithCircuitBreaker(operation: suspend () -> T): T {
        checkAndUpdateState()

        when (state.get()) {
            State.OPEN -> {
                logger.warn("Circuit $name is OPEN, rejecting call")
                throw CircuitBreakerOpenException("Circuit $name is open")
            }
            State.HALF_OPEN -> {
                if (halfOpenCallCount.incrementAndGet() > halfOpenMaxCalls) {
                    logger.warn("Circuit $name is HALF_OPEN but max test calls reached, rejecting call")
                    throw CircuitBreakerOpenException("Circuit $name is half-open but max test calls reached")
                }
                logger.info("Circuit $name is HALF_OPEN, allowing test call ${halfOpenCallCount.get()}/$halfOpenMaxCalls")
            }
            State.CLOSED -> {
                // Normal operation, proceed with call
            }
        }

        return try {
            val result = operation()
            recordSuccess()
            result
        } catch (e: Exception) {
            recordFailure(e)
            throw e
        }
    }

    private suspend fun checkAndUpdateState() {
        val currentState = state.get()
        val now = Instant.now()

        if (currentState == State.OPEN) {
            val elapsedTime = now.toEpochMilli() - lastStateChange.get().toEpochMilli()
            if (elapsedTime >= resetTimeout) {
                mutex.withLock {
                    if (state.get() == State.OPEN) {
                        logger.info("Circuit $name reset timeout reached, transitioning to HALF_OPEN")
                        state.set(State.HALF_OPEN)
                        halfOpenCallCount.set(0)
                        lastStateChange.set(now)
                    }
                }
            }
        }
    }

    private suspend fun recordSuccess() {
        mutex.withLock {
            when (state.get()) {
                State.HALF_OPEN -> {
                    if (halfOpenCallCount.get() >= halfOpenMaxCalls) {
                        logger.info("Circuit $name successful test calls completed, transitioning to CLOSED")
                        state.set(State.CLOSED)
                        failureCount.set(0)
                        lastStateChange.set(Instant.now())
                    }
                }
                State.CLOSED -> {
                    failureCount.set(0)
                }
                else -> { /* No action needed for other states */ }
            }
        }
    }

    private suspend fun recordFailure(exception: Exception) {
        mutex.withLock {
            when (state.get()) {
                State.CLOSED -> {
                    val failures = failureCount.incrementAndGet()
                    logger.warn("Circuit $name failure recorded: $failures/$failureThreshold", exception)
                    
                    if (failures >= failureThreshold) {
                        logger.warn("Circuit $name failure threshold reached, transitioning to OPEN")
                        state.set(State.OPEN)
                        lastStateChange.set(Instant.now())
                    }
                }
                State.HALF_OPEN -> {
                    logger.warn("Circuit $name test call failed, transitioning back to OPEN", exception)
                    state.set(State.OPEN)
                    lastStateChange.set(Instant.now())
                }
                else -> { /* No action needed for other states */ }
            }
        }
    }

    /**
     * Gets the current state of the circuit breaker.
     *
     * @return The current state
     */
    fun getState(): State = state.get()

    /**
     * Resets the circuit breaker to its initial closed state.
     * This is primarily for testing purposes.
     */
    suspend fun reset() {
        mutex.withLock {
            state.set(State.CLOSED)
            failureCount.set(0)
            halfOpenCallCount.set(0)
            lastStateChange.set(Instant.now())
            logger.info("Circuit $name manually reset to CLOSED state")
        }
    }
}

/**
 * Exception thrown when a circuit is open and a call is attempted.
 */
class CircuitBreakerOpenException(message: String) : Exception(message)
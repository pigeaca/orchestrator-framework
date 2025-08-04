package com.orchestrator.util

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.pow

/**
 * Utility class for implementing retry logic with exponential backoff.
 * Provides methods to retry operations that might fail temporarily.
 */
object RetryHelper {
    private val logger = LoggerFactory.getLogger(RetryHelper::class.java)

    /**
     * Executes the given operation with retry logic.
     *
     * @param maxAttempts The maximum number of attempts to make
     * @param initialDelayMs The initial delay between retries in milliseconds
     * @param maxDelayMs The maximum delay between retries in milliseconds
     * @param factor The factor by which the delay increases with each retry
     * @param operation The operation to execute
     * @return The result of the operation
     * @throws Exception The last exception that occurred if all attempts fail
     */
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 100,
        maxDelayMs: Long = 5000,
        factor: Double = 2.0,
        operation: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                logger.warn("Operation failed (attempt ${attempt + 1}/$maxAttempts): ${e.message}")
                
                if (attempt < maxAttempts - 1) {
                    logger.debug("Retrying in $currentDelay ms")
                    // Delay before next retry
                    delay(currentDelay)
                    // Calculate next delay with exponential backoff
                    currentDelay = min(maxDelayMs, (currentDelay * factor).toLong())
                }
            }
        }

        throw lastException ?: IllegalStateException("Operation failed after $maxAttempts attempts")
    }

    /**
     * Executes the given operation with retry logic, returning null on failure.
     *
     * @param maxAttempts The maximum number of attempts to make
     * @param initialDelayMs The initial delay between retries in milliseconds
     * @param maxDelayMs The maximum delay between retries in milliseconds
     * @param factor The factor by which the delay increases with each retry
     * @param operation The operation to execute
     * @return The result of the operation or null if all attempts fail
     */
    suspend fun <T> withRetryOrNull(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 100,
        maxDelayMs: Long = 5000,
        factor: Double = 2.0,
        operation: suspend () -> T
    ): T? {
        return try {
            withRetry(maxAttempts, initialDelayMs, maxDelayMs, factor, operation)
        } catch (e: Exception) {
            logger.error("Operation failed after $maxAttempts attempts: ${e.message}")
            null
        }
    }
}
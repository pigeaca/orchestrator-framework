package com.application.orchestrator.util

import com.google.protobuf.ByteString

/**
 * Utility class for input parameter validation.
 * Provides common validation methods used across the application.
 */
object ValidationUtils {

    /**
     * Validates that a string is not null or empty.
     *
     * @param value The string to validate
     * @param paramName The name of the parameter (for error message)
     * @throws IllegalArgumentException if the string is null or empty
     */
    fun validateNotEmpty(value: String?, paramName: String) {
        if (value.isNullOrEmpty()) {
            throw IllegalArgumentException("$paramName cannot be null or empty")
        }
    }

    /**
     * Validates that an object is not null.
     *
     * @param value The object to validate
     * @param paramName The name of the parameter (for error message)
     * @throws IllegalArgumentException if the object is null
     */
    fun validateNotNull(value: Any?, paramName: String) {
        if (value == null) {
            throw IllegalArgumentException("$paramName cannot be null")
        }
    }

    /**
     * Validates that a collection is not null or empty.
     *
     * @param collection The collection to validate
     * @param paramName The name of the parameter (for error message)
     * @throws IllegalArgumentException if the collection is null or empty
     */
    fun validateNotEmpty(collection: Collection<*>?, paramName: String) {
        if (collection.isNullOrEmpty()) {
            throw IllegalArgumentException("$paramName cannot be null or empty")
        }
    }

    /**
     * Validates that a ByteString is not null or empty.
     *
     * @param value The ByteString to validate
     * @param paramName The name of the parameter (for error message)
     * @throws IllegalArgumentException if the ByteString is null or empty
     */
    fun validateNotEmpty(value: ByteString?, paramName: String) {
        if (value == null || value.isEmpty) {
            throw IllegalArgumentException("$paramName cannot be null or empty")
        }
    }

    /**
     * Validates that a value is positive.
     *
     * @param value The value to validate
     * @param paramName The name of the parameter (for error message)
     * @throws IllegalArgumentException if the value is not positive
     */
    fun validatePositive(value: Int, paramName: String) {
        if (value <= 0) {
            throw IllegalArgumentException("$paramName must be positive")
        }
    }

    /**
     * Validates that a value is positive.
     *
     * @param value The value to validate
     * @param paramName The name of the parameter (for error message)
     * @throws IllegalArgumentException if the value is not positive
     */
    fun validatePositive(value: Long, paramName: String) {
        if (value <= 0) {
            throw IllegalArgumentException("$paramName must be positive")
        }
    }
}
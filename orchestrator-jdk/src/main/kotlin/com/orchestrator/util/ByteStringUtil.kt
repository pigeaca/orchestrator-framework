package com.orchestrator.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import com.google.protobuf.UnsafeByteOperations
import java.nio.ByteBuffer

/**
 * Utility class for optimized ByteString operations.
 * 
 * This class provides methods for efficient conversion between ByteString and other data types,
 * avoiding unnecessary copying of data when possible.
 */
object ByteStringUtil {

    /**
     * Converts an object to ByteString using the provided ObjectMapper.
     * Uses UnsafeByteOperations to avoid copying the byte array.
     *
     * @param obj The object to convert
     * @param objectMapper The ObjectMapper to use for serialization
     * @return ByteString representation of the object
     */
    fun toByteString(obj: Any?, objectMapper: ObjectMapper): ByteString {
        if (obj == null) return ByteString.EMPTY
        
        val bytes = objectMapper.writeValueAsBytes(obj)
        return UnsafeByteOperations.unsafeWrap(bytes)
    }

    /**
     * Converts a ByteArray to ByteString without copying the data.
     * Uses UnsafeByteOperations for better performance.
     *
     * @param bytes The byte array to convert
     * @return ByteString representation of the byte array
     */
    fun wrapBytes(bytes: ByteArray?): ByteString {
        if (bytes == null || bytes.isEmpty()) return ByteString.EMPTY
        
        return UnsafeByteOperations.unsafeWrap(bytes)
    }

    /**
     * Converts a ByteBuffer to ByteString without copying the data.
     * Uses UnsafeByteOperations for better performance.
     *
     * @param buffer The ByteBuffer to convert
     * @return ByteString representation of the ByteBuffer
     */
    fun wrapBuffer(buffer: ByteBuffer?): ByteString {
        if (buffer == null || !buffer.hasRemaining()) return ByteString.EMPTY
        
        return UnsafeByteOperations.unsafeWrap(buffer)
    }

    /**
     * Converts a ByteString to an object using the provided ObjectMapper.
     *
     * @param byteString The ByteString to convert
     * @param clazz The class of the object to deserialize to
     * @param objectMapper The ObjectMapper to use for deserialization
     * @return The deserialized object
     */
    fun <T> fromByteString(byteString: ByteString?, clazz: Class<T>, objectMapper: ObjectMapper): T? {
        if (byteString == null || byteString.isEmpty) return null
        
        return objectMapper.readValue(byteString.toByteArray(), clazz)
    }
    
    /**
     * Converts a ByteString to an object using the provided ObjectMapper and TypeReference.
     *
     * @param byteString The ByteString to convert
     * @param typeReference The TypeReference for the object to deserialize to
     * @param objectMapper The ObjectMapper to use for deserialization
     * @return The deserialized object
     */
    fun <T> fromByteString(byteString: ByteString?, typeReference: TypeReference<T>, objectMapper: ObjectMapper): T? {
        if (byteString == null || byteString.isEmpty) return null
        
        return objectMapper.readValue(byteString.toByteArray(), typeReference)
    }
    
    /**
     * Converts a ByteString to an object using the provided ObjectMapper and JavaType.
     *
     * @param byteString The ByteString to convert
     * @param javaType The JavaType for the object to deserialize to
     * @param objectMapper The ObjectMapper to use for deserialization
     * @return The deserialized object
     */
    fun <T> fromByteString(byteString: ByteString?, javaType: JavaType, objectMapper: ObjectMapper): T? {
        if (byteString == null || byteString.isEmpty) return null
        
        return objectMapper.readValue(byteString.toByteArray(), javaType)
    }
}
package com.application.orchestrator.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Configuration properties for Redis connection.
 * Includes settings for connection pooling and general Redis configuration.
 */
@Component
@ConfigurationProperties(prefix = "spring.redis")
class RedisProperties {
    // Basic Redis connection properties
    var host: String = "localhost"
    var port: Int = 6379
    var password: String? = null
    var database: Int = 0
    var timeout: Long = 2000
    
    // Connection pool properties
    var pool = Pool()
    
    /**
     * Redis connection pool configuration.
     */
    class Pool {
        // Maximum number of idle connections in the pool
        var maxIdle: Int = 8
        
        // Minimum number of idle connections in the pool
        var minIdle: Int = 0
        
        // Maximum number of connections that can be allocated by the pool at a given time
        var maxActive: Int = 8
        
        // Maximum amount of time a connection allocation should block before throwing an exception
        var maxWait: Long = -1
        
        // Time between runs of the idle object evictor thread
        var timeBetweenEvictionRuns: Long = 30000
    }
}
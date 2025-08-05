package com.application.orchestrator.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/**
 * Configuration for queue settings.
 * Provides centralized configuration for all queue-related properties.
 */
@Configuration
open class QueueConfig {

    /**
     * Properties for interpreter queue configuration.
     */
    @Component
    @ConfigurationProperties(prefix = "orchestrator.queue.interpreter")
    class InterpreterQueueProperties {
        /**
         * Maximum capacity of the interpreter queue.
         * Limits the number of tasks that can be queued to prevent memory exhaustion.
         */
        var capacity: Int = 1000
        
        /**
         * Timeout in milliseconds for offering tasks to the queue.
         * If a task cannot be added to the queue within this time, it will be rejected.
         */
        var offerTimeoutMs: Long = 100
        
        /**
         * Maximum number of tasks to process in a single batch.
         * Helps optimize throughput by processing multiple tasks at once.
         */
        var batchSize: Int = 10
    }
    
    /**
     * Properties for activity queue configuration.
     */
    @Component
    @ConfigurationProperties(prefix = "orchestrator.queue.activity")
    class ActivityQueueProperties {
        /**
         * Maximum capacity of each activity queue.
         * Limits the number of tasks that can be queued to prevent memory exhaustion.
         */
        var capacity: Int = 1000
        
        /**
         * Timeout in milliseconds for offering tasks to the queue.
         * If a task cannot be added to the queue within this time, it will be rejected.
         */
        var offerTimeoutMs: Long = 100
        
        /**
         * Maximum number of tasks to process in a single batch.
         * Helps optimize throughput by processing multiple tasks at once.
         */
        var batchSize: Int = 10
        
        /**
         * Default timeout in seconds for activity task execution.
         * If an activity takes longer than this time, it may be considered failed.
         */
        var defaultTimeoutSec: Long = 60
    }
}
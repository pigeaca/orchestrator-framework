package com.orchestrator.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.UUID

/**
 * Configuration properties for interpreter workers.
 * Includes settings for horizontal scaling and task polling.
 */
@ConfigurationProperties(prefix = "workflow.interpreter")
class InterpreterProperties {
    /**
     * Delay between task polling attempts in milliseconds.
     */
    var fetchDelay: Long = 100
    
    /**
     * Unique ID for this worker instance.
     * Defaults to a random UUID if not specified.
     * Used for task distribution in horizontally scaled environments.
     */
    var workerId: String = UUID.randomUUID().toString()
    
    /**
     * Total number of worker instances in the cluster.
     * Used for task distribution in horizontally scaled environments.
     * Defaults to 1 (no horizontal scaling).
     */
    var workerCount: Int = 1
    
    /**
     * Maximum number of tasks to process in a single batch.
     * Helps optimize throughput by processing multiple tasks at once.
     */
    var batchSize: Int = 10
}
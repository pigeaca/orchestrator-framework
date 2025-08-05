package com.application.orchestrator.service.activity

import com.orchestrator.proto.ActivityTask

/**
 * Interface for managing activity task queues.
 * Supports batching for improved throughput under high load.
 */
interface ActivityQueueManager {
    /**
     * Initializes the activity queues.
     * 
     * @param queues The collection of queue names to initialize
     */
    suspend fun init(queues: Collection<String>)
    
    /**
     * Polls for a single activity task from the specified queue.
     * This method is maintained for backward compatibility.
     * 
     * @param queue The queue to poll from
     * @return The activity task or null if no task is available
     */
    suspend fun pollTask(queue: String): ActivityTask?
    
    /**
     * Polls for multiple activity tasks from the specified queue.
     * Enables batch processing for improved throughput.
     * 
     * @param queue The queue to poll from
     * @param batchSize The maximum number of tasks to poll
     * @return A list of activity tasks, may be empty if no tasks are available
     */
    suspend fun pollTasks(queue: String, batchSize: Int): List<ActivityTask>
    
    /**
     * Submits an activity task to the queue.
     * 
     * @param task The activity task to submit
     */
    suspend fun submitTask(task: ActivityTask)
    
    /**
     * Submits multiple activity tasks to their respective queues.
     * Enables batch processing for improved throughput.
     * 
     * @param tasks The list of activity tasks to submit
     */
    suspend fun submitTasks(tasks: List<ActivityTask>)
}
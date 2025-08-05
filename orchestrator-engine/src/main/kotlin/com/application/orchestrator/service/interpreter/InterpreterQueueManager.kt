package com.application.orchestrator.service.interpreter

import com.orchestrator.proto.InterpreterWorkerTask

/**
 * Interface for managing interpreter task queues.
 * Supports horizontal scaling through worker ID-based task distribution.
 */
interface InterpreterQueueManager {
    /**
     * Polls for available interpreter tasks.
     * 
     * @return A list of interpreter worker tasks
     */
    suspend fun pollTasks(): List<InterpreterWorkerTask>
    
    /**
     * Polls for available interpreter tasks for a specific worker.
     * Used for horizontal scaling to distribute tasks among multiple workers.
     * 
     * @param workerId The unique ID of the worker
     * @param workerCount The total number of workers in the cluster
     * @param batchSize The maximum number of tasks to return
     * @return A list of interpreter worker tasks assigned to this worker
     */
    suspend fun pollTasksForWorker(workerId: String, workerCount: Int, batchSize: Int): List<InterpreterWorkerTask>
    
    /**
     * Submits an interpreter task to the queue.
     * 
     * @param task The interpreter worker task to submit
     */
    suspend fun submitTask(task: InterpreterWorkerTask)
}
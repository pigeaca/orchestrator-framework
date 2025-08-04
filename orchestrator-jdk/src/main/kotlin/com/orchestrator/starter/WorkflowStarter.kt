package com.orchestrator.starter

import java.util.concurrent.CompletableFuture

interface WorkflowStarter {
    /**
     * Starts a workflow execution with the given name and input data.
     *
     * @param workflowName The name of the workflow to start
     * @param inputData The input data for the workflow
     * @param version Optional version of the workflow to start, defaults to latest version
     * @return A CompletableFuture that will be completed with the workflow result
     */
    suspend fun <D, R> startWorkflow(
        workflowName: String, 
        inputData: D, 
        version: String? = null
    ): CompletableFuture<R>
}
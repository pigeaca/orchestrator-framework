package com.orchestrator.starter

import java.util.concurrent.CompletableFuture

interface WorkflowStarter {
    suspend fun <D, R> startWorkflow(workflowName: String, inputData: D): CompletableFuture<R>
}
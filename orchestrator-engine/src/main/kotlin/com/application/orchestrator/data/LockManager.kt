package com.application.orchestrator.data

import java.time.Duration

interface WorkflowLockManager {
    suspend fun tryAcquire(workflowId: String, ownerId: String, ttl: Duration): Boolean
    suspend fun release(workflowId: String, ownerId: String)
}
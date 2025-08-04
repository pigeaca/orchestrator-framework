package com.application.orchestrator.data

import com.application.orchestrator.engine.WorkflowInstance
import com.google.protobuf.ByteString
import java.util.function.BiFunction

/**
 * Interface for managing workflow data.
 * Provides methods to create, update, and retrieve workflow instances.
 */
interface OrchestratorWorkflowData {
    /**
     * Creates a new workflow instance.
     *
     * @param workflowId The ID of the workflow
     * @param workflowName The name of the workflow
     * @param request The request data for the workflow
     */
    fun createWorkflowInstance(workflowId: String, workflowName: String, request: ByteString)
    
    /**
     * Updates a workflow instance if it exists.
     *
     * @param workflowId The ID of the workflow
     * @param updateFunction The function to apply to update the workflow instance
     * @return The updated workflow instance, or null if not found
     */
    suspend fun updateWorkflowIfPresent(workflowId: String, updateFunction: BiFunction<String, WorkflowInstance, WorkflowInstance>): WorkflowInstance?

    /**
     * Polls for a workflow request.
     *
     * @param workflowId The ID of the workflow
     * @return The workflow request data, or null if not found
     */
    suspend fun pollWorkflowRequest(workflowId: String): ByteString?
    
    /**
     * Polls for a workflow response.
     *
     * @param workflowId The ID of the workflow
     * @return The workflow response data, or null if not found
     */
    suspend fun pollWorkflowResponse(workflowId: String): ByteString?
}
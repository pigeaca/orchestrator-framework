package com.application.orchestrator.data

import com.application.orchestrator.data.impl.WorkflowStep
import com.application.orchestrator.engine.StepStatus
import java.time.Instant

/**
 * Interface for managing workflow steps data.
 * Provides methods to save, load, and update workflow steps.
 */
interface OrchestratorWorkflowStepData {
    suspend fun saveStep(workflowId: String, step: WorkflowStep)
    suspend fun loadAllSteps(workflowId: String): List<WorkflowStep>
    suspend fun updateStepStatus(workflowId: String, stepId: String, status: StepStatus)
    suspend fun findStepsByStatus(workflowId: String, status: StepStatus): List<WorkflowStep>
    suspend fun findStepsTimedOut(now: Instant): List<WorkflowStep>
}
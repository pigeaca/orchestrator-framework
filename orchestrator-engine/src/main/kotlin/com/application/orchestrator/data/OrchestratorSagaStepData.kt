package com.application.orchestrator.data

import com.application.orchestrator.data.impl.WorkflowStep
import com.application.orchestrator.engine.StepStatus
import java.time.Instant

interface OrchestratorSagaStepData {
    suspend fun saveStep(sagaId: String, step: WorkflowStep)
    suspend fun loadAllSteps(sagaId: String): List<WorkflowStep>
    suspend fun updateStepStatus(sagaId: String, stepId: String, status: StepStatus)
    suspend fun findStepsByStatus(sagaId: String, status: StepStatus): List<WorkflowStep>
    suspend fun findStepsTimedOut(now: Instant): List<WorkflowStep>}
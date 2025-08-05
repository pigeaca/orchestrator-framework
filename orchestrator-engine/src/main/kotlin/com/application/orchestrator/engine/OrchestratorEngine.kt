package com.application.orchestrator.engine

import com.application.orchestrator.data.OrchestratorActivityResultData
import com.application.orchestrator.data.OrchestratorWorkflowStepData
import com.application.orchestrator.data.OrchestratorWorkflowData
import com.application.orchestrator.data.WorkflowLockManager
import com.application.orchestrator.data.impl.WorkflowStep
import com.application.orchestrator.service.activity.ActivityQueueManager
import com.application.orchestrator.service.interpreter.InterpreterQueueManager
import com.application.orchestrator.service.interpreter.toActivityTask
import com.application.orchestrator.service.interpreter.toModel
import com.application.orchestrator.util.RetryHelper
import com.application.orchestrator.util.ValidationUtils
import com.google.protobuf.ByteString
import com.orchestrator.interpreter.dsl.ExecutionPlan
import com.orchestrator.interpreter.dsl.ExecutionStep
import com.orchestrator.proto.ActivityResult
import com.orchestrator.proto.InterpreterWorkerResult
import com.orchestrator.proto.InterpreterWorkerTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Central component of the orchestration system responsible for managing the lifecycle
 * of distributed workflows (sagas).
 *
 * <p>This engine coordinates the execution of steps, handles step results, manages rollback chains,
 * and ensures state consistency across the distributed system.</p>
 *
 * <h2>Main Responsibilities:</h2>
 * <ul>
 *   <li>Initialize and start new saga workflows</li>
 *   <li>Route step execution requests to the appropriate service queues</li>
 *   <li>Track and store intermediate step results</li>
 *   <li>Trigger rollback steps in case of failure</li>
 *   <li>Provide polling interface for activity workers</li>
 *   <li>Expose status of running/completed workflows</li>
 * </ul>
 *
 * <p>The engine communicates with external components (activity workers, gRPC clients) via
 * a reactive and pluggable protocol layer and stores workflow state in Redis for durability.</p>
 *
 * <h3>Typical Usage:</h3>
 * <pre>{@code orchestratorEngine.startWorkflow("UserRegistration", inputPayload)}</pre>
 *
 */
@Service
class OrchestratorEngine(
    private val activityQueueManager: ActivityQueueManager,
    private val interpreterQueueManager: InterpreterQueueManager,
    private val orchestratorWorkflowData: OrchestratorWorkflowData,
    private val orchestratorWorkflowStepData: OrchestratorWorkflowStepData,
    private val orchestratorActivityResultData: OrchestratorActivityResultData,
    private val lockManager: WorkflowLockManager
) : OrchestratorEngineInterface {
    private val logger = LoggerFactory.getLogger(OrchestratorEngine::class.java)
    private val instanceId = UUID.randomUUID().toString()

    private val executionPlans = mutableMapOf<String, ExecutionPlan>()

    /**
     * Starts a new workflow execution.
     *
     * @param workflowName The name of the workflow to start
     * @param inputData The input data for the workflow
     * @return The ID of the created workflow instance
     * @throws IllegalArgumentException if workflowName is empty or inputData is empty
     */
    override suspend fun startWorkflow(workflowName: String, inputData: ByteString): String {
        // Validate input parameters
        ValidationUtils.validateNotEmpty(workflowName, "workflowName")
        ValidationUtils.validateNotEmpty(inputData, "inputData")
        
        logger.info("Started workflow for workflowName=${workflowName}")
        
        // Check if execution plan exists for the workflow
        val executionPlan = executionPlans[workflowName]
        if (executionPlan == null) {
            logger.warn("No execution plan found for workflowName=${workflowName}")
            return ""
        }
        
        // Check if execution plan has steps
        if (executionPlan.steps.isEmpty()) {
            logger.warn("Execution plan for workflowName=${workflowName} has no steps")
            return ""
        }
        
        val workflowId = UUID.randomUUID().toString()
        val firstStepId = UUID.randomUUID().toString()
        val firstExecutionStep = executionPlan.steps.first()

        // Create workflow instance with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            orchestratorWorkflowData.createWorkflowInstance(workflowId, workflowName, inputData)
        }
        
        val interpreterWorkerTask = firstExecutionStep.toModel(true, firstStepId, workflowId)
        
        // Submit task with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            interpreterQueueManager.submitTask(interpreterWorkerTask)
        }

        // Save step with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            orchestratorWorkflowStepData.saveStep(workflowId, firstExecutionStep.toWorkflowStep(firstStepId, workflowId))
        }
        return workflowId
    }

    /**
     * Submits execution plans to the orchestrator.
     *
     * @param executionPlans The list of execution plans to submit
     * @throws IllegalArgumentException if executionPlans is empty
     */
    override suspend fun submitExecutionPlain(executionPlans: List<ExecutionPlan>) {
        // Validate input parameters
        ValidationUtils.validateNotEmpty(executionPlans, "executionPlans")
        
        logger.info("Received execution plans steps=$executionPlans")
        
        executionPlans.forEach { executionPlan ->
            // Validate execution plan name
            if (executionPlan.name.isBlank()) {
                logger.warn("Skipping execution plan with null or blank name")
                return@forEach
            }
            
            this.executionPlans.computeIfAbsent(executionPlan.name) { executionPlan }
            
            // Validate execution plan steps
            if (executionPlan.steps.isEmpty()) {
                logger.warn("Execution plan ${executionPlan.name} has no steps")
                return@forEach
            }
            
            val queues = executionPlan.steps
                .mapNotNull { step -> step.queue.takeIf { it.isNotBlank() } }
                .toSet()
                
            if (queues.isNotEmpty()) {
                activityQueueManager.init(queues)
            }
        }
    }

    /**
     * Submits interpreter results for processing.
     *
     * @param interpreterResults The list of interpreter results to process
     * @throws IllegalArgumentException if interpreterResults is empty
     */
    override suspend fun submitInterpreterResults(interpreterResults: List<InterpreterWorkerResult>) {
        // Validate input parameters
        ValidationUtils.validateNotEmpty(interpreterResults, "interpreterResults")
        
        logger.info("Processing ${interpreterResults.size} interpreter results")
        
        interpreterResults.forEach { interpreterResult ->
            if (validateInterpreterResult(interpreterResult)) {
                processInterpreterResult(interpreterResult)
            }
        }
    }
    
    /**
     * Validates an interpreter result.
     *
     * @param interpreterResult The interpreter result to validate
     * @return true if the result is valid, false otherwise
     */
    private fun validateInterpreterResult(interpreterResult: InterpreterWorkerResult): Boolean {
        try {
            ValidationUtils.validateNotNull(interpreterResult, "interpreterResult")
            
            if (interpreterResult.sagaId.isNullOrBlank()) {
                logger.warn("Skipping interpreter result with null or blank sagaId")
                return false
            }
            
            if (interpreterResult.taskType.isNullOrBlank()) {
                logger.warn("Skipping interpreter result with null or blank taskType for sagaId=${interpreterResult.sagaId}")
                return false
            }
            
            return true
        } catch (e: Exception) {
            logger.warn("Invalid interpreter result: ${e.message}")
            return false
        }
    }
    
    private suspend fun processInterpreterResult(interpreterResult: InterpreterWorkerResult) {
        if (!acquireWorkflowLock(interpreterResult.sagaId)) {
            logger.warn("Could not acquire lock for sagaId=${interpreterResult.sagaId}")
            return
        }
        
        try {
            if (interpreterResult.taskType == "onFinish") {
                handleFinishTask(interpreterResult)
            } else {
                handleRegularTask(interpreterResult)
            }
        } catch (e: Exception) {
            logger.error("Error processing interpreter result for sagaId=${interpreterResult.sagaId}", e)
        } finally {
            releaseWorkflowLock(interpreterResult.sagaId)
        }
    }
    
    private suspend fun handleFinishTask(interpreterResult: InterpreterWorkerResult) {
        logger.info("Task was completed")
        orchestratorWorkflowData.updateWorkflowIfPresent(interpreterResult.sagaId) { _, workflowInstance ->
            workflowInstance.response = interpreterResult.output.toByteArray()
            workflowInstance
        }
    }
    
    private suspend fun handleRegularTask(interpreterResult: InterpreterWorkerResult) {
        val activityTask = interpreterResult.toActivityTask()
        activityQueueManager.submitTask(activityTask)
        logger.info("Submitted next activity task ActivityTask=${activityTask}")
    }

    /**
     * Submits an activity result for processing.
     *
     * @param activityResult The activity result to process
     * @throws IllegalArgumentException if activityResult is invalid
     */
    override suspend fun submitActivityResult(activityResult: ActivityResult) {
        // Validate input parameters
        ValidationUtils.validateNotNull(activityResult, "activityResult")
        
        if (!validateActivityResult(activityResult)) {
            return
        }
        
        if (!acquireWorkflowLock(activityResult.sagaId)) {
            logger.warn("Could not acquire lock for sagaId=${activityResult.sagaId}")
            return
        }

        try {
            logger.info("Received activity result activityResult=${activityResult}")
            val executionPlan = getExecutionPlanOrReturn(activityResult.sagaName)
            
            if (executionPlan == null) {
                logger.warn("No execution plan found for workflowName=${activityResult.sagaName}")
                return
            }

            processActivityResult(activityResult, executionPlan)
        } catch (e: Exception) {
            logger.error("Error processing activity result for sagaId=${activityResult.sagaId}", e)
        } finally {
            releaseWorkflowLock(activityResult.sagaId)
        }
    }
    
    /**
     * Validates an activity result.
     *
     * @param activityResult The activity result to validate
     * @return true if the result is valid, false otherwise
     */
    private fun validateActivityResult(activityResult: ActivityResult): Boolean {
        try {
            if (activityResult.sagaId.isNullOrBlank()) {
                logger.warn("Skipping activity result with null or blank sagaId")
                return false
            }
            
            if (activityResult.sagaName.isNullOrBlank()) {
                logger.warn("Skipping activity result with null or blank sagaName for sagaId=${activityResult.sagaId}")
                return false
            }
            
            if (activityResult.stepId.isNullOrBlank()) {
                logger.warn("Skipping activity result with null or blank stepId for sagaId=${activityResult.sagaId}")
                return false
            }
            
            if (activityResult.stepType.isNullOrBlank()) {
                logger.warn("Skipping activity result with null or blank stepType for sagaId=${activityResult.sagaId}")
                return false
            }
            
            return true
        } catch (e: Exception) {
            logger.warn("Invalid activity result: ${e.message}")
            return false
        }
    }
    
    private suspend fun acquireWorkflowLock(sagaId: String): Boolean {
        return RetryHelper.withRetryOrNull(maxAttempts = 3) {
            lockManager.tryAcquire(sagaId, instanceId, Duration.ofSeconds(30))
        } ?: false
    }
    
    private suspend fun releaseWorkflowLock(sagaId: String) {
        RetryHelper.withRetryOrNull(maxAttempts = 3) {
            lockManager.release(sagaId, instanceId)
        }
    }
    
    /**
     * Gets the execution plan for the specified workflow name.
     *
     * @param workflowName The name of the workflow
     * @return The execution plan, or null if not found
     */
    private fun getExecutionPlanOrReturn(workflowName: String): ExecutionPlan? {
        return executionPlans[workflowName]
    }
    
    private suspend fun processActivityResult(activityResult: ActivityResult, executionPlan: ExecutionPlan) {
        if (activityResult.success) {
            startNextStep(activityResult, executionPlan)
        } else {
            startRollbackChain(activityResult, executionPlan)
        }
    }

    private suspend fun startRollbackChain(activityResult: ActivityResult, executionPlan: ExecutionPlan) {
        if (activityResult.sagaId.isNullOrBlank() || activityResult.stepId.isNullOrBlank() || activityResult.stepType.isNullOrBlank()) {
            logger.warn("Cannot start rollback chain with invalid activity result: $activityResult")
            return
        }
        
        try {
            updateWorkflowStatusToRollback(activityResult.sagaId)
            markStepAsFailed(activityResult.sagaId, activityResult.stepId)
            
            val currentStep = findCurrentStep(executionPlan, activityResult.stepType)
            if (currentStep == null) {
                logger.warn("No step found with type ${activityResult.stepType} in execution plan for saga ${activityResult.sagaName}")
                return
            }
            
            if (currentStep.rollbackStepIds.isEmpty()) {
                logger.info("No rollback steps defined for step type ${activityResult.stepType}")
                return
            }
            
            submitRollbackSteps(currentStep, executionPlan, activityResult)
        } catch (e: Exception) {
            logger.error("Error starting rollback chain for sagaId=${activityResult.sagaId}", e)
        }
    }
    
    private suspend fun updateWorkflowStatusToRollback(sagaId: String) {
        // Update workflow status with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            orchestratorWorkflowData.updateWorkflowIfPresent(sagaId) { _, sagaInstance ->
                sagaInstance.copy(workflowStatus = WorkflowStatus.UNDER_ROLLBACK)
            }
        }
    }
    
    private suspend fun markStepAsFailed(sagaId: String, stepId: String) {
        // Update step status with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            orchestratorWorkflowStepData.updateStepStatus(sagaId, stepId, StepStatus.FAILED)
        }
    }
    
    private fun findCurrentStep(executionPlan: ExecutionPlan, stepType: String): ExecutionStep? {
        return executionPlan.steps.find { it.stepType == stepType }
    }
    
    private suspend fun submitRollbackSteps(currentStep: ExecutionStep, executionPlan: ExecutionPlan, activityResult: ActivityResult) {
        val rollbackSteps = executionPlan.steps.filter { it.stepType in currentStep.rollbackStepIds }
        
        rollbackSteps.forEach { step ->
            val stepId = UUID.randomUUID().toString()
            submitRollbackStep(step, stepId, activityResult.sagaId)
        }
    }
    
    private suspend fun submitRollbackStep(step: ExecutionStep, stepId: String, sagaId: String) {
        val interpreterWorkerTask = step.toModel(false, stepId, sagaId)
        
        // Save step with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            orchestratorWorkflowStepData.saveStep(
                workflowId = sagaId, step.toWorkflowStep(stepId, sagaId)
            )
        }
        
        // Submit task with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            interpreterQueueManager.submitTask(interpreterWorkerTask)
        }
    }

    private suspend fun startNextStep(activityResult: ActivityResult, executionPlan: ExecutionPlan) {
        if (activityResult.sagaId.isNullOrBlank() || activityResult.stepId.isNullOrBlank() || 
            activityResult.stepType.isNullOrBlank() || activityResult.sagaName.isNullOrBlank()) {
            logger.warn("Cannot start next step with invalid activity result: $activityResult")
            return
        }
        
        try {
            updateActivityResultAndStatus(activityResult)
            
            val readySteps = findReadySteps(executionPlan, activityResult.stepType)
            
            if (readySteps.isEmpty()) {
                logger.info("No next steps found for step type ${activityResult.stepType}, starting finish step")
                startFinishStep(activityResult)
            } else {
                logger.info("Found ${readySteps.size} next steps for step type ${activityResult.stepType}")
                submitNextSteps(readySteps, activityResult)
            }
        } catch (e: Exception) {
            logger.error("Error starting next step for sagaId=${activityResult.sagaId}", e)
        }
    }
    
    private suspend fun updateActivityResultAndStatus(activityResult: ActivityResult) {
        // Update activity result with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            orchestratorActivityResultData.updateActivityResul(
                activityResult.sagaId,
                activityResult.stepType,
                activityResult.output
            )
        }
        
        // Update step status with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            orchestratorWorkflowStepData.updateStepStatus(
                activityResult.sagaId, 
                activityResult.stepId, 
                StepStatus.COMPLETED
            )
        }
    }
    
    private fun findReadySteps(executionPlan: ExecutionPlan, completedStepType: String): List<ExecutionStep> {
        return executionPlan.steps.filter { step -> step.dependencies.contains(completedStepType) }
    }
    
    private suspend fun submitNextSteps(readySteps: List<ExecutionStep>, activityResult: ActivityResult) {
        readySteps.forEach { step ->
            submitNextStep(step, activityResult.sagaId)
        }
    }
    
    private suspend fun submitNextStep(step: ExecutionStep, sagaId: String) {
        val stepId = UUID.randomUUID().toString()
        val interpreterWorkerTask = step.toModel(true, stepId, sagaId)
        
        // Save step with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            orchestratorWorkflowStepData.saveStep(
                workflowId = sagaId, step.toWorkflowStep(stepId, sagaId)
            )
        }
        
        // Submit task with retry
        RetryHelper.withRetry(maxAttempts = 3) {
            interpreterQueueManager.submitTask(interpreterWorkerTask)
        }
        
        logger.info("Submitted next interpreter task InterpreterWorkerTask=${interpreterWorkerTask}")
    }

    private suspend fun startFinishStep(activityResult: ActivityResult) {
        if (activityResult.sagaId.isNullOrBlank() || activityResult.stepId.isNullOrBlank() || 
            activityResult.sagaName.isNullOrBlank()) {
            logger.warn("Cannot start finish step with invalid activity result: $activityResult")
            return
        }
        
        try {
            val updatedWorkflow = updateWorkflowStatus(activityResult.sagaId)
            
            if (updatedWorkflow == null) {
                logger.warn("No workflow instance found for sagaId=${activityResult.sagaId}")
                return
            }
            
            submitFinishTask(activityResult, updatedWorkflow)
            logger.info("Submitted finish task for sagaId=${activityResult.sagaId} with status=${updatedWorkflow.workflowStatus}")
        } catch (e: Exception) {
            logger.error("Error starting finish step for sagaId=${activityResult.sagaId}", e)
        }
    }
    
    private suspend fun updateWorkflowStatus(sagaId: String): WorkflowInstance? {
        return orchestratorWorkflowData.updateWorkflowIfPresent(sagaId) { _, sagaInstance ->
            val workflowStatus = determineWorkflowStatus(sagaInstance.workflowStatus)
            sagaInstance.copy(workflowStatus = workflowStatus)
        }
    }
    
    private fun determineWorkflowStatus(currentStatus: WorkflowStatus): WorkflowStatus {
        return if (currentStatus == WorkflowStatus.UNDER_ROLLBACK) {
            WorkflowStatus.FAILED
        } else {
            WorkflowStatus.COMPLETED
        }
    }
    
    private suspend fun submitFinishTask(activityResult: ActivityResult, workflowInstance: WorkflowInstance) {
        val interpreterWorkerTask = createFinishTask(
            activityResult.sagaId,
            activityResult.sagaName,
            activityResult.stepId,
            workflowInstance.workflowStatus == WorkflowStatus.COMPLETED
        )
        interpreterQueueManager.submitTask(interpreterWorkerTask)
    }
    
    private fun createFinishTask(
        sagaId: String,
        sagaType: String,
        stepId: String,
        isSuccessful: Boolean
    ): InterpreterWorkerTask {
        return InterpreterWorkerTask.newBuilder()
            .setSagaId(sagaId)
            .setSagaType(sagaType)
            .setStepId(stepId)
            .setType("onFinish")
            .setQueue("")
            .setSuccess(isSuccessful)
            .build()
    }
}

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
    COMPLETED
}

enum class WorkflowStatus {
    IN_PROGRESS,
    UNDER_ROLLBACK,
    COMPLETED,
    FAILED
}

data class WorkflowInstance(
    val workflowStatus: WorkflowStatus,
    val workflowId: String,
    val workflowName: String,
    val request: ByteArray,
    var response: ByteArray?,
)

fun ExecutionStep.toWorkflowStep(stepId: String, sagaId: String): WorkflowStep = WorkflowStep(
    stepId = stepId,
    workflowId = sagaId,
    type = stepType,
    status = StepStatus.PENDING,
    queue = queue,
    timeout = Duration.ofSeconds(10),
    updatedAt = Instant.now(),
    maxAttempts = 3
)
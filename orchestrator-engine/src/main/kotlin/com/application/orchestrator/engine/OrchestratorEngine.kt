package com.application.orchestrator.engine

import com.application.orchestrator.data.OrchestratorActivityResultData
import com.application.orchestrator.data.OrchestratorSagaStepData
import com.application.orchestrator.data.OrchestratorWorkflowData
import com.application.orchestrator.data.WorkflowLockManager
import com.application.orchestrator.data.impl.WorkflowStep
import com.application.orchestrator.service.activity.ActivityQueueManager
import com.application.orchestrator.service.interpreter.InterpreterQueueManager
import com.application.orchestrator.service.interpreter.toActivityTask
import com.application.orchestrator.service.interpreter.toModel
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
    private val orchestratorSagaStepData: OrchestratorSagaStepData,
    private val orchestratorActivityResultData: OrchestratorActivityResultData,
    private val lockManager: WorkflowLockManager
) {
    private val logger = LoggerFactory.getLogger(OrchestratorEngine::class.java)
    private val instanceId = UUID.randomUUID().toString()

    private val executionPlans = mutableMapOf<String, ExecutionPlan>()

    suspend fun startWorkflow(sagaName: String, inputData: ByteString): String {
        logger.info("Started workflow for sagaName=${sagaName}")
        val executionPlan = executionPlans[sagaName] ?: return ""
        val sagaId = UUID.randomUUID().toString()

        val firstStepId = UUID.randomUUID().toString()
        val firstExecutionStep = executionPlan.steps.first()

        orchestratorWorkflowData.createSagaInstance(sagaId, sagaName, inputData)
        val interpreterWorkerTask = firstExecutionStep.toModel(true, firstStepId, sagaId)
        interpreterQueueManager.submitTask(interpreterWorkerTask)

        orchestratorSagaStepData.saveStep(sagaId, firstExecutionStep.toWorkflowStep(firstStepId, sagaId))
        return sagaId
    }

    suspend fun submitExecutionPlain(executionPlans: List<ExecutionPlan>) {
        logger.info("Received execution plans steps=$executionPlans")
        executionPlans.forEach { executionPlan ->
            this.executionPlans.computeIfAbsent(executionPlan.name) { executionPlan }
            val queues = executionPlan.steps.map { it.queue }.toSet()
            activityQueueManager.init(queues)
        }
    }

    suspend fun submitInterpreterResults(interpreterResults: List<InterpreterWorkerResult>) {
        interpreterResults.forEach { interpreterResult ->
            val acquired = lockManager.tryAcquire(interpreterResult.sagaId, instanceId, Duration.ofSeconds(30))
            if (!acquired) return
            try {
                if (interpreterResult.taskType == "onFinish") {
                    logger.info("Task was completed")
                    orchestratorWorkflowData.updateSagaIfPresent(interpreterResult.sagaId) { _, workflowInstance ->
                        workflowInstance.response = interpreterResult.output.toByteArray()
                        workflowInstance
                    }
                } else {
                    val activityTask = interpreterResult.toActivityTask()
                    activityQueueManager.submitTask(activityTask)
                    logger.info("Submitted next activity task ActivityTask=${activityTask}")
                }
            } finally {
                lockManager.release(interpreterResult.sagaId, instanceId)
            }
        }
    }

    suspend fun submitActivityResult(activityResult: ActivityResult) {
        val acquired = lockManager.tryAcquire(activityResult.sagaId, instanceId, Duration.ofSeconds(30))
        if (!acquired) return

        try {
            logger.info("Received activity result activityResult=${activityResult}")
            val executionPlan = executionPlans[activityResult.sagaName] ?: return

            if (activityResult.success) {
                startNextStep(activityResult, executionPlan)
            } else {
                startRollbackChain(activityResult, executionPlan)
            }
        } finally {
            lockManager.release(activityResult.sagaId, instanceId)
        }
    }

    private suspend fun startRollbackChain(activityResult: ActivityResult, executionPlan: ExecutionPlan) {
        orchestratorWorkflowData.updateSagaIfPresent(activityResult.sagaId) { _, sagaInstance ->
            val newSagaInstance = sagaInstance.copy(workflowStatus = WorkflowStatus.UNDER_ROLLBACK)
            newSagaInstance
        }

        orchestratorSagaStepData.updateStepStatus(activityResult.sagaId, activityResult.stepId, StepStatus.FAILED)

        val currentStep = executionPlan.steps.find { it.stepType == activityResult.stepType } ?: return
        executionPlan.steps.filter { it.stepType in currentStep.rollbackStepIds }.forEach { step ->
            val stepId = UUID.randomUUID().toString()
            val interpreterWorkerTask = step.toModel(false, stepId, activityResult.sagaId)
            orchestratorSagaStepData.saveStep(
                sagaId = activityResult.sagaId, step.toWorkflowStep(stepId, activityResult.sagaId)
            )
            interpreterQueueManager.submitTask(interpreterWorkerTask)
        }

    }

    private suspend fun startNextStep(activityResult: ActivityResult, executionPlan: ExecutionPlan) {
        orchestratorActivityResultData.updateActivityResul(
            activityResult.sagaId,
            activityResult.stepType,
            activityResult.output
        )
        orchestratorSagaStepData.updateStepStatus(activityResult.sagaId, activityResult.stepId, StepStatus.COMPLETED)

        val readySteps = executionPlan.steps.filter { step -> step.dependencies.contains(activityResult.stepType) }
        readySteps.forEach { step ->
            val stepId = UUID.randomUUID().toString()
            val interpreterWorkerTask = step.toModel(true, stepId, activityResult.sagaId)
            orchestratorSagaStepData.saveStep(
                sagaId = activityResult.sagaId, step.toWorkflowStep(stepId, activityResult.sagaId)
            )
            interpreterQueueManager.submitTask(interpreterWorkerTask)
            logger.info("Submitted next interpreter task InterpreterWorkerTask=${interpreterWorkerTask}")
        }
        if (readySteps.isEmpty()) {
            startFinishStep(activityResult)
        }

    }

    private suspend fun startFinishStep(activityResult: ActivityResult) {
        orchestratorWorkflowData.updateSagaIfPresent(activityResult.sagaId) { _, sagaInstance ->
            val workflowStatus =
                if (sagaInstance.workflowStatus == WorkflowStatus.UNDER_ROLLBACK) WorkflowStatus.FAILED else WorkflowStatus.COMPLETED
            sagaInstance.copy(workflowStatus = workflowStatus)
        }?.let {
            val interpreterWorkerTask = InterpreterWorkerTask.newBuilder()
                .setSagaId(activityResult.sagaId)
                .setSagaType(activityResult.sagaName)
                .setStepId(activityResult.stepId)
                .setType("onFinish")
                .setQueue("")
                .setSuccess(it.workflowStatus == WorkflowStatus.COMPLETED)
                .build()
            interpreterQueueManager.submitTask(interpreterWorkerTask)
        }
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
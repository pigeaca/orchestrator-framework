package com.application.orchestrator.engine

import com.application.orchestrator.data.OrchestratorActivityResultData
import com.application.orchestrator.data.OrchestratorSagaData
import com.application.orchestrator.service.activity.ActivityQueueManager
import com.application.orchestrator.service.interpreter.InterpreterQueueManager
import com.application.orchestrator.service.interpreter.toActivityTask
import com.application.orchestrator.service.interpreter.toModel
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.ByteString
import com.orchestrator.activity.test.CreateUserResponse
import com.orchestrator.interpreter.dsl.ExecutionPlan
import com.orchestrator.proto.ActivityResult
import com.orchestrator.proto.InterpreterWorkerResult
import com.orchestrator.proto.InterpreterWorkerTask
import org.springframework.stereotype.Service
import java.util.*

@Service
class OrchestratorEngine(
    private val activityQueueManager: ActivityQueueManager,
    private val interpreterQueueManager: InterpreterQueueManager,
    private val orchestratorSagaData: OrchestratorSagaData,
    private val orchestratorActivityResultData: OrchestratorActivityResultData
) {
    //sagaName -> instance
    private val executionPlans = mutableMapOf<String, ExecutionPlan>()

    suspend fun startWorkflow(sagaName: String, inputData: ByteString): String {
        println("Started workflow for sagaName=${sagaName}")
        val executionPlan = executionPlans[sagaName] ?: return ""
        val sagaId = UUID.randomUUID().toString()

        orchestratorActivityResultData.initActivityResult(sagaId)

        val firstStepId = UUID.randomUUID().toString()
        val firstExecutionStep = executionPlan.steps.first()

        orchestratorSagaData.createSagaInstance(sagaId, sagaName, inputData)
        val interpreterWorkerTask = firstExecutionStep.toModel(true, firstStepId, sagaName)
        interpreterQueueManager.submitTask(interpreterWorkerTask)
        return sagaId
    }

    suspend fun submitExecutionPlain(executionPlans: List<ExecutionPlan>) {
        println("Received execution plans steps=$executionPlans")
        executionPlans.forEach { executionPlan ->
            this.executionPlans.computeIfAbsent(executionPlan.name) { executionPlan }
            val queues = executionPlan.steps.map { it.queue }.toSet()
            activityQueueManager.init(queues)
        }
    }

    suspend fun submitInterpreterResults(interpreterResults: List<InterpreterWorkerResult>) {
        interpreterResults.forEach { interpreterResult ->
            if (interpreterResult.taskType == "onFinish") {
                println("Task was completed")
            } else {
                val activityTask = interpreterResult.toActivityTask()
                activityQueueManager.submitTask(activityTask)
                println("Submitted next activity task ActivityTask=${activityTask}")
            }
        }
    }

    suspend fun submitActivityResult(activityResult: ActivityResult) {
        println("Received activity result activityResult=${activityResult}")
        val executionPlan = executionPlans[activityResult.sagaName]

        if (activityResult.success) {
            val isUpdated = orchestratorActivityResultData.updateActivityResul(activityResult.sagaId, activityResult.stepId, activityResult.output)

            if (isUpdated) {
                val readySteps = executionPlan?.steps?.filter { step ->
                    step.dependencies.contains(activityResult.stepType)
                }
                readySteps?.forEach { step ->
                    val interpreterWorkerTask = step.toModel(true, UUID.randomUUID().toString(), activityResult.sagaId)
                    interpreterQueueManager.submitTask(interpreterWorkerTask)
                    println("Submitted next interpreter task InterpreterWorkerTask=${interpreterWorkerTask}")
                }
                if (readySteps?.isEmpty() == true) {
                    orchestratorSagaData.updateSagaIfPresent(activityResult.sagaId) { _, sagaInstance ->
                        val sagaStatus =
                            if (sagaInstance.sagaStatus == SagaStatus.UNDER_ROLLBACK) SagaStatus.FAILED else SagaStatus.COMPLETED
                        sagaInstance.copy(sagaStatus = sagaStatus)
                    }?.let {
                        val interpreterWorkerTask = InterpreterWorkerTask.newBuilder()
                            .setSagaId(activityResult.sagaId)
                            .setSagaType(activityResult.sagaName)
                            .setStepId(activityResult.stepId)
                            .setType("onFinish")
                            .setQueue("")
                            .setSuccess(it.sagaStatus == SagaStatus.COMPLETED)
                            .build()
                        interpreterQueueManager.submitTask(interpreterWorkerTask)
                    }
                }
            }
        } else {
            orchestratorSagaData.updateSagaIfPresent(activityResult.sagaId) { _, sagaInstance ->
                val newSagaInstance = sagaInstance.copy(sagaStatus = SagaStatus.UNDER_ROLLBACK)
                newSagaInstance
            }?.let {
                val currentStep = executionPlan?.steps?.find { it.stepType == activityResult.stepType } ?: return
                val rollbackSteps = executionPlan.steps.filter { it.stepType in currentStep.rollbackStepIds }
                rollbackSteps.forEach { step ->
                    val interpreterWorkerTask = step.toModel(false, UUID.randomUUID().toString(), activityResult.sagaId)
                    interpreterQueueManager.submitTask(interpreterWorkerTask)
                }
            }
        }
    }
}

enum class StepStatus {
    PENDING,
    READY,
    IN_PROGRESS,
    COMPLETED
}

enum class SagaStatus {
    IN_PROGRESS,
    UNDER_ROLLBACK,
    COMPLETED,
    FAILED
}

data class SagaInstance(
    val sagaStatus: SagaStatus,
    val sagaId: String,
    val sagaName: String,
    val request: ByteString,
    val response: ByteString?,
)
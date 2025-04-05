package com.application.orchestrator.service.interpreter.impl

import com.application.orchestrator.data.OrchestratorActivityResultData
import com.application.orchestrator.data.OrchestratorSagaData
import com.application.orchestrator.engine.OrchestratorEngine
import com.application.orchestrator.service.interpreter.InterpreterQueueManager
import com.application.orchestrator.service.interpreter.InterpreterWorkerTaskPollingService
import com.google.protobuf.ByteString
import com.orchestrator.interpreter.dsl.ExecutionPlan
import com.orchestrator.proto.InterpreterWorkerResult
import com.orchestrator.proto.InterpreterWorkerTask
import org.springframework.stereotype.Service

@Service
class InterpreterWorkerTaskPollingServiceImpl(
    private val orchestratorEngine: OrchestratorEngine,
    private val interpreterQueueManager: InterpreterQueueManager,
    private val activityResultData: OrchestratorActivityResultData,
    private val orchestratorSagaData: OrchestratorSagaData
) :
    InterpreterWorkerTaskPollingService {

    override suspend fun sendExecutionPlain(executionPlans: List<ExecutionPlan>) {
        orchestratorEngine.submitExecutionPlain(executionPlans)
    }

    override suspend fun pollTasks(): List<InterpreterWorkerTask> {
        return interpreterQueueManager.pollTasks()
    }

    override suspend fun submitResults(interpreterResults: List<InterpreterWorkerResult>) {
        orchestratorEngine.submitInterpreterResults(interpreterResults)
    }

    override suspend fun pollData(sagaId: String, stepId: String, stepName: String): ByteString {
        println("Requested result for sagaId=${sagaId} stepType=${stepName} from stepId=${stepId}")
        return activityResultData.getActivityResult(sagaId, stepName) ?: ByteString.EMPTY
    }

    override suspend fun pollWorkflowRequest(sagaId: String): ByteString {
        println("Requested request for sagaId=${sagaId}")
        orchestratorSagaData.pollWorkflowRequest(sagaId)
        return orchestratorSagaData.pollWorkflowRequest(sagaId) ?: ByteString.EMPTY
    }

}
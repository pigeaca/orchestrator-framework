package com.application.orchestrator.grpc

import com.application.orchestrator.service.interpreter.InterpreterWorkerTaskPollingService
import com.orchestrator.interpreter.dsl.ExecutionPlan
import com.orchestrator.interpreter.dsl.ExecutionStep
import com.orchestrator.proto.*
import org.springframework.stereotype.Service

@Service
class InterpreterGrpcWorkerTaskPollingService(
    private val interpreterGrpcWorkerTaskPollingService: InterpreterWorkerTaskPollingService
) : InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineImplBase() {
    override suspend fun pollData(request: PollDataRequest): DataResponse {
        val data = interpreterGrpcWorkerTaskPollingService.pollData(request.sagaId, request.stepId, request.stepName)
        return DataResponse.newBuilder()
            .setData(data)
            .build()
    }

    override suspend fun pollTasks(request: Empty): InterpreterWorkerTaskList {
        val interpreterWorkerTasks = interpreterGrpcWorkerTaskPollingService.pollTasks()
        return InterpreterWorkerTaskList.newBuilder()
            .addAllTasks(interpreterWorkerTasks)
            .build()
    }

    override suspend fun pollWorkflowRequest(request: PollWorkflowRequestInput): DataResponse {
        val workflowRequest = interpreterGrpcWorkerTaskPollingService.pollWorkflowRequest(request.sagaId)
        return DataResponse.newBuilder()
            .setData(workflowRequest)
            .build()
    }

    override suspend fun sendExecutionPlan(request: SendExecutionPlanRequest): Empty {
        val executionPlans = request.plansList.map { executionPlan ->
            ExecutionPlan(
                name = executionPlan.name,
                steps = executionPlan.stepsList.map { step ->
                    ExecutionStep(
                        stepType = step.stepType,
                        sagaType = step.sagaType,
                        queue = step.queue,
                        dependencies = step.dependenciesList.map { it },
                        rollbackStepIds = step.rollbackStepIdsList.map { it },
                    )
                }
            )
        }
        interpreterGrpcWorkerTaskPollingService.sendExecutionPlain(executionPlans)
        return Empty.getDefaultInstance()
    }

    override suspend fun submitResults(request: InterpreterWorkerResultList): Empty {
        interpreterGrpcWorkerTaskPollingService.submitResults(request.resultsList)
        return Empty.getDefaultInstance()
    }
}
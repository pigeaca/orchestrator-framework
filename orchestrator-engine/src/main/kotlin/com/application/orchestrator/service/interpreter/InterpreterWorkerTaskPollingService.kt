package com.application.orchestrator.service.interpreter

import com.google.protobuf.ByteString
import com.orchestrator.interpreter.dsl.ExecutionPlan
import com.orchestrator.interpreter.dsl.ExecutionStep
import com.orchestrator.proto.ActivityTask
import com.orchestrator.proto.InterpreterWorkerResult
import com.orchestrator.proto.InterpreterWorkerTask

interface InterpreterWorkerTaskPollingService {
    suspend fun sendExecutionPlain(executionPlans: List<ExecutionPlan>)
    suspend fun pollTasks(): List<InterpreterWorkerTask>
    suspend fun submitResults(interpreterResults: List<InterpreterWorkerResult>)
    suspend fun pollData(sagaId: String, stepId: String, stepName: String): ByteString
    suspend fun pollWorkflowRequest(sagaId: String): ByteString
}

fun InterpreterWorkerResult.toActivityTask(): ActivityTask = ActivityTask.newBuilder()
    .setServiceName(this.serviceName)
    .setMethodName(this.methodName)
    .setSagaName(this.sagaName)
    .setStepId(stepId)
    .setSagaId(this.sagaId)
    .setQueue(this.queue)
    .setInput(this.output)
    .setType(this.taskType)
    .build()

fun ExecutionStep.toModel(success: Boolean, stepId: String, sagaId: String): InterpreterWorkerTask =
    InterpreterWorkerTask
        .newBuilder()
        .setSagaType(sagaType)
        .setQueue(queue)
        .setSuccess(success)
        .setType(stepType)
        .setStepId(stepId)
        .setSagaId(sagaId)
        .build()
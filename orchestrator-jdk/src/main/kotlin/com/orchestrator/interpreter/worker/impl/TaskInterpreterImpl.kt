package com.orchestrator.interpreter.worker.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.ByteString
import com.orchestrator.interpreter.dsl.*
import com.orchestrator.interpreter.worker.TaskInterpreter
import com.orchestrator.proto.*
import kotlin.reflect.KClass


class DefaultResultProvider(
    private val sagaId: String,
    private val stepId: String,
    private val objectMapper: ObjectMapper,
    private val interpreterWorkerTaskPollingService: InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub
) : ResultProvider {

    override suspend fun <T : Any> resultOf(stepType: String, requestType: KClass<T>): T? {
        val pollDataRequest = PollDataRequest
            .newBuilder()
            .setStepId(stepId)
            .setSagaId(sagaId)
            .setStepName(stepType)
            .build()
        val dataResponse = interpreterWorkerTaskPollingService.pollData(pollDataRequest)
        if (dataResponse.data.isEmpty) {
            return null
        }
        val value = objectMapper.readValue(dataResponse.data.toByteArray(), requestType.java)
        return value
    }

    override suspend fun <T : Any> useRequest(requestType: KClass<T>): T {
        val pollWorkflowRequest = PollWorkflowRequestInput.newBuilder().setSagaId(sagaId).build()
        val dataResponse = interpreterWorkerTaskPollingService.pollWorkflowRequest(pollWorkflowRequest)
        return objectMapper.readValue(dataResponse.data.toByteArray(), requestType.java)
    }
}

class TaskInterpreterImpl(
    //TODO decouple request/polling
    private val interpreterWorkerTaskPollingService: InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub
) : TaskInterpreter {
    private val stepMap = mutableMapOf<String, Map<String, Step<*, *>>>()
    private val objectMapper = jacksonObjectMapper()

    override suspend fun interpreterTasks(
        interpreterWorkerTasks: List<InterpreterWorkerTask>,
        orchestrationDefinitionsGroup: Map<String, OrchestrationDefinition<*, *>>
    ) = interpreterWorkerTasks.mapNotNull {
        val orchestrationDefinition = orchestrationDefinitionsGroup[it.sagaType] ?: return@mapNotNull null

        orchestrationDefinition.workflowResponse
        //TODO do not create provider each time
        val defaultResultProvider = DefaultResultProvider(
            stepId = it.stepId,
            sagaId = it.sagaId,
            objectMapper = objectMapper,
            interpreterWorkerTaskPollingService = interpreterWorkerTaskPollingService
        )
        val stepContext = StepContext(defaultResultProvider)

        if (it.type == "onFinish") {
            val result = if (it.success) orchestrationDefinition.onComplete?.invoke(stepContext) else
                orchestrationDefinition.onFailure?.invoke(stepContext)

            return@mapNotNull InterpreterWorkerResult.newBuilder()
                .setServiceName("")
                .setMethodName("")
                .setSagaId(it.sagaId)
                .setStepId(it.stepId)
                .setSagaName(it.sagaType)
                .setTaskType(it.type)
                .setQueue(it.queue)
                .setSuccess(true)
                .setOutput(ByteString.copyFrom(objectMapper.writeValueAsBytes(result)))
                .build()
        }

        val stepMap = stepMap.computeIfAbsent(orchestrationDefinition.name) { orchestrationDefinition.buildStepMap() }
        val step = stepMap[it.type] ?: return@mapNotNull null

        val activityPayload = getActivityPayload(step.call, stepContext)
        val output = if (activityPayload == null) ByteString.EMPTY else ByteString.copyFrom(objectMapper.writeValueAsBytes(activityPayload))
        println("Task was interpreter stepId=${it.stepId} sagaId=${it.sagaId} type=${it.type}")
        InterpreterWorkerResult.newBuilder()
            .setServiceName(step.call.serviceName)
            .setMethodName(step.call.methodName)
            .setSagaId(it.sagaId)
            .setStepId(it.stepId)
            .setSagaName(it.sagaType)
            .setTaskType(it.type)
            .setQueue(it.queue)
            .setSuccess(true)
            .setOutput(output)
            .build()
    }
}
package com.orchestrator.starter.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.ByteString
import com.orchestrator.proto.GetWorkflowResultRequest
import com.orchestrator.proto.StartWorkflowRequest
import com.orchestrator.proto.WorkflowEngineServiceGrpcKt
import com.orchestrator.starter.WorkflowStarter
import kotlinx.coroutines.*
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

@Service
class WorkflowStarterImpl(
    private val workflowEngineServiceCoroutineStub: WorkflowEngineServiceGrpcKt.WorkflowEngineServiceCoroutineStub,
) : WorkflowStarter {
    private val objectMapper = jacksonObjectMapper()
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun <D, R> startWorkflow(workflowName: String, inputData: D): CompletableFuture<R> {
        val valueAsBytes = objectMapper.writeValueAsBytes(inputData)
        val request = StartWorkflowRequest.newBuilder()
            .setSagaName(workflowName)
            .setInputData(ByteString.copyFrom(valueAsBytes))
            .build()

        val response = workflowEngineServiceCoroutineStub.startWorkflow(request)
        val sagaId = response.sagaId

        val future = CompletableFuture<R>()
        val timeoutMillis = 30_000L
        val pollingInterval = 500L

        val startedAt = System.currentTimeMillis()

        scope.launch {
            while (!future.isDone) {
                delay(pollingInterval)

                val now = System.currentTimeMillis()
                if (now - startedAt > timeoutMillis) {
                    future.completeExceptionally(TimeoutException("Workflow $sagaId timed out"))
                    break
                }

                val result = workflowEngineServiceCoroutineStub.tryGetWorkflowResult(
                    GetWorkflowResultRequest.newBuilder().setSagaId(sagaId).build()
                )

                if (result.inputData != null && !result.inputData.isEmpty) {
                    val value = objectMapper.readValue(result.inputData.toByteArray(), object : TypeReference<R>() {})
                    future.complete(value)
                    break
                }
            }
        }

        return future
    }
}
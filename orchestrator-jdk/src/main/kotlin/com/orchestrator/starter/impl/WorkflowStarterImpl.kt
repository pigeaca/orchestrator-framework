package com.orchestrator.starter.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.ByteString
import com.orchestrator.proto.GetWorkflowResultRequest
import com.orchestrator.proto.StartWorkflowRequest
import com.orchestrator.proto.WorkflowEngineServiceGrpcKt
import com.orchestrator.starter.WorkflowStarter
import com.orchestrator.util.ByteStringUtil
import com.orchestrator.util.ValidationUtils
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

@Service
class WorkflowStarterImpl(
    private val workflowEngineServiceCoroutineStub: WorkflowEngineServiceGrpcKt.WorkflowEngineServiceCoroutineStub,
) : WorkflowStarter {
    private val logger = LoggerFactory.getLogger(WorkflowStarterImpl::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Starts a workflow execution with the given name and input data.
     *
     * @param workflowName The name of the workflow to start
     * @param inputData The input data for the workflow
     * @param version Optional version of the workflow to start, defaults to latest version
     * @return A CompletableFuture that will be completed with the workflow result
     * @throws IllegalArgumentException if workflowName is empty or inputData is null
     */
    override suspend fun <D, R> startWorkflow(
        workflowName: String, 
        inputData: D, 
        version: String?
    ): CompletableFuture<R> {
        // Validate input parameters
        ValidationUtils.validateNotEmpty(workflowName, "workflowName")
        ValidationUtils.validateNotNull(inputData, "inputData")
        
        logger.info("Starting workflow: $workflowName${version?.let { ", version: $it" } ?: ""}")
        
        // Use ByteStringUtil for efficient ByteString creation
        val optimizedByteString = ByteStringUtil.toByteString(inputData, objectMapper)
        val requestBuilder = StartWorkflowRequest.newBuilder()
            .setSagaName(workflowName)
            .setInputData(optimizedByteString)
            
        // Add version if specified
        if (version != null) {
            val method = requestBuilder.javaClass.getMethod("setVersion", String::class.java)
            method.invoke(requestBuilder, version)
        }
        
        val request = requestBuilder.build()

        val response = workflowEngineServiceCoroutineStub.startWorkflow(request)
        val sagaId = response.sagaId
        
        logger.info("Workflow started with ID: $sagaId")

        val future = CompletableFuture<R>()
        val timeoutMillis = 30_000L
        val pollingInterval = 500L

        val startedAt = System.currentTimeMillis()

        scope.launch {
            while (!future.isDone) {
                delay(pollingInterval)

                val now = System.currentTimeMillis()
                if (now - startedAt > timeoutMillis) {
                    logger.warn("Workflow $sagaId timed out after ${timeoutMillis}ms")
                    future.completeExceptionally(TimeoutException("Workflow $sagaId timed out"))
                    break
                }

                val result = workflowEngineServiceCoroutineStub.tryGetWorkflowResult(
                    GetWorkflowResultRequest.newBuilder().setSagaId(sagaId).build()
                )

                if (result.inputData != null && !result.inputData.isEmpty) {
                    val typeRef = object : TypeReference<R>() {}
                    val value = ByteStringUtil.fromByteString(result.inputData, typeRef, objectMapper)
                    logger.info("Workflow $sagaId completed successfully")
                    future.complete(value)
                    break
                }
            }
        }

        return future
    }
}
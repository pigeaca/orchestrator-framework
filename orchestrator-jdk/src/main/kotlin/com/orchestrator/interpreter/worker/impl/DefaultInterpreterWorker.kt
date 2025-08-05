package com.orchestrator.interpreter.worker.impl

import com.orchestrator.config.InterpreterProperties
import com.orchestrator.interpreter.dsl.OrchestrationDefinition
import com.orchestrator.interpreter.dsl.compileToExecutionPlan
import com.orchestrator.interpreter.dsl.toProto
import com.orchestrator.interpreter.service.OrchestratorDefinitionProvider
import com.orchestrator.interpreter.worker.InterpreterWorker
import com.orchestrator.interpreter.worker.TaskInterpreter
import com.orchestrator.proto.Empty
import com.orchestrator.proto.InterpreterWorkerResultList
import com.orchestrator.proto.InterpreterWorkerServiceGrpcKt
import com.orchestrator.proto.SendExecutionPlanRequest
import com.orchestrator.util.RetryHelper
import com.orchestrator.util.ValidationUtils
import io.grpc.Metadata
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import javax.annotation.PreDestroy

/**
 * Default implementation of InterpreterWorker.
 * Handles the interpretation of workflow tasks.
 */
class DefaultInterpreterWorker(
    private val definitions: List<OrchestratorDefinitionProvider>,
    private val interpreterWorkerTaskPollingService: InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub,
    private val taskInterpreter: TaskInterpreter,
    private val interpreterProperties: InterpreterProperties
) : InterpreterWorker {
    private val logger = LoggerFactory.getLogger(DefaultInterpreterWorker::class.java)
    private var job: Job? = null

    /**
     * Starts the interpreter worker.
     * Compiles execution plans from definitions and begins polling for tasks.
     */
    override fun startInterpreter() {
        // Validate input parameters
        ValidationUtils.validateNotEmpty(definitions, "definitions")
        ValidationUtils.validateNotNull(interpreterWorkerTaskPollingService, "interpreterWorkerTaskPollingService")
        ValidationUtils.validateNotNull(taskInterpreter, "taskInterpreter")
        ValidationUtils.validateNotNull(interpreterProperties, "interpreterProperties")
        ValidationUtils.validatePositive(interpreterProperties.fetchDelay, "interpreterProperties.fetchDelay")
        
        logger.info("Starting interpreter worker with ${definitions.size} workflow definitions")
        
        val orchestrationDefinitions: List<OrchestrationDefinition<*, *>> = definitions.map { it.definition() }
        val executionPlans = orchestrationDefinitions.map { it.compileToExecutionPlan() }
        val orchestrationDefinitionsGroup = orchestrationDefinitions.associateBy { it.name }
        
        logger.info("Compiled ${executionPlans.size} execution plans")
        
        this.job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // Send execution plans to the engine with retry
            RetryHelper.withRetry(maxAttempts = 3) {
                interpreterWorkerTaskPollingService.sendExecutionPlan(
                    SendExecutionPlanRequest
                        .newBuilder()
                        .addAllPlans(executionPlans.map { it.toProto() })
                        .build()
                )
            }
            
            logger.info("Execution plans sent to engine, starting task polling loop")
            
            while (true) {
                try {
                    // Poll tasks with retry, using worker ID for horizontal scaling
                    val metadata = Metadata()
                    metadata.put(Metadata.Key.of("worker-id", Metadata.ASCII_STRING_MARSHALLER), interpreterProperties.workerId)
                    metadata.put(Metadata.Key.of("worker-count", Metadata.ASCII_STRING_MARSHALLER), interpreterProperties.workerCount.toString())
                    metadata.put(Metadata.Key.of("batch-size", Metadata.ASCII_STRING_MARSHALLER), interpreterProperties.batchSize.toString())
                    
                    val interpreterWorkerTasks = RetryHelper.withRetryOrNull(maxAttempts = 3) {
                        interpreterWorkerTaskPollingService
                            .withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(metadata))
                            .pollTasks(Empty.getDefaultInstance())
                    } ?: continue // Skip this iteration if polling failed after retries
                    
                    if (interpreterWorkerTasks.tasksList.isNotEmpty()) {
                        logger.debug("Received ${interpreterWorkerTasks.tasksList.size} tasks to process")
                        
                        val interpreterWorkerResults =
                            taskInterpreter.interpreterTasks(interpreterWorkerTasks.tasksList, orchestrationDefinitionsGroup)
                        
                        // Submit results with retry
                        RetryHelper.withRetryOrNull(maxAttempts = 3) {
                            interpreterWorkerTaskPollingService.submitResults(InterpreterWorkerResultList
                                .newBuilder()
                                .addAllResults(interpreterWorkerResults)
                                .build())
                            
                            logger.debug("Submitted ${interpreterWorkerResults.size} results")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error in interpreter worker task polling loop", e)
                }
                
                delay(interpreterProperties.fetchDelay)
            }
        }
    }

    /**
     * Stops the interpreter worker.
     * Cancels the polling job.
     */
    @PreDestroy
    override fun stopInterpreter() {
        logger.info("Stopping interpreter worker")
        this.job?.cancel()
    }
}
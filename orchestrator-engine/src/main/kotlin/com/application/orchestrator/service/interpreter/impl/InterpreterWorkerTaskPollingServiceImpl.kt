package com.application.orchestrator.service.interpreter.impl

import com.application.orchestrator.data.OrchestratorActivityResultData
import com.application.orchestrator.data.OrchestratorWorkflowData
import com.application.orchestrator.engine.OrchestratorEngineInterface
import com.application.orchestrator.service.interpreter.InterpreterQueueManager
import com.application.orchestrator.service.interpreter.InterpreterWorkerTaskPollingService
import com.application.orchestrator.util.ValidationUtils
import com.google.protobuf.ByteString
import com.orchestrator.interpreter.dsl.ExecutionPlan
import com.orchestrator.proto.InterpreterWorkerResult
import com.orchestrator.proto.InterpreterWorkerTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Implementation of InterpreterWorkerTaskPollingService.
 * Handles communication between the orchestrator engine and interpreter workers.
 */
@Service
class InterpreterWorkerTaskPollingServiceImpl(
    private val orchestratorEngine: OrchestratorEngineInterface,
    private val interpreterQueueManager: InterpreterQueueManager,
    private val activityResultData: OrchestratorActivityResultData,
    private val orchestratorWorkflowData: OrchestratorWorkflowData
) : InterpreterWorkerTaskPollingService {
    private val logger = LoggerFactory.getLogger(InterpreterWorkerTaskPollingServiceImpl::class.java)

    /**
     * Sends execution plans to the orchestrator engine.
     *
     * @param executionPlans The list of execution plans to send
     * @throws IllegalArgumentException if executionPlans is null or empty
     */
    override suspend fun sendExecutionPlain(executionPlans: List<ExecutionPlan>) {
        ValidationUtils.validateNotEmpty(executionPlans, "executionPlans")
        logger.debug("Sending ${executionPlans.size} execution plans to engine")
        orchestratorEngine.submitExecutionPlain(executionPlans)
    }

    /**
     * Polls for interpreter worker tasks.
     * This method is maintained for backward compatibility.
     *
     * @return A list of interpreter worker tasks
     */
    override suspend fun pollTasks(): List<InterpreterWorkerTask> {
        logger.debug("Polling for interpreter tasks (legacy mode)")
        return interpreterQueueManager.pollTasks()
    }
    
    /**
     * Polls for interpreter worker tasks for a specific worker.
     * Used for horizontal scaling to distribute tasks among multiple workers.
     *
     * @param workerId The unique ID of the worker
     * @param workerCount The total number of workers in the cluster
     * @param batchSize The maximum number of tasks to return
     * @return A list of interpreter worker tasks assigned to this worker
     * @throws IllegalArgumentException if any parameter is invalid
     */
    override suspend fun pollTasksForWorker(workerId: String, workerCount: Int, batchSize: Int): List<InterpreterWorkerTask> {
        ValidationUtils.validateNotEmpty(workerId, "workerId")
        ValidationUtils.validatePositive(workerCount, "workerCount")
        ValidationUtils.validatePositive(batchSize, "batchSize")
        
        logger.debug("Polling for interpreter tasks (worker=$workerId, count=$workerCount, batchSize=$batchSize)")
        return interpreterQueueManager.pollTasksForWorker(workerId, workerCount, batchSize)
    }

    /**
     * Submits interpreter worker results to the orchestrator engine.
     *
     * @param interpreterResults The list of interpreter results to submit
     * @throws IllegalArgumentException if interpreterResults is null or empty
     */
    override suspend fun submitResults(interpreterResults: List<InterpreterWorkerResult>) {
        ValidationUtils.validateNotEmpty(interpreterResults, "interpreterResults")
        logger.debug("Submitting ${interpreterResults.size} interpreter results")
        orchestratorEngine.submitInterpreterResults(interpreterResults)
    }

    /**
     * Polls for activity result data for a specific workflow step.
     *
     * @param sagaId The ID of the workflow
     * @param stepId The ID of the step
     * @param stepName The name of the step
     * @return The activity result data as ByteString, or empty if not found
     * @throws IllegalArgumentException if any parameter is empty
     */
    override suspend fun pollData(sagaId: String, stepId: String, stepName: String): ByteString {
        ValidationUtils.validateNotEmpty(sagaId, "sagaId")
        ValidationUtils.validateNotEmpty(stepId, "stepId")
        ValidationUtils.validateNotEmpty(stepName, "stepName")
        
        logger.debug("Polling data for workflow=$sagaId, step=$stepId, stepName=$stepName")
        return activityResultData.getActivityResult(sagaId, stepName) ?: ByteString.EMPTY
    }

    /**
     * Polls for a workflow request.
     *
     * @param sagaId The ID of the workflow
     * @return The workflow request as ByteString, or empty if not found
     * @throws IllegalArgumentException if sagaId is empty
     */
    override suspend fun pollWorkflowRequest(sagaId: String): ByteString {
        ValidationUtils.validateNotEmpty(sagaId, "sagaId")
        
        logger.debug("Polling workflow request for workflow=$sagaId")
        return orchestratorWorkflowData.pollWorkflowRequest(sagaId) ?: ByteString.EMPTY
    }
}
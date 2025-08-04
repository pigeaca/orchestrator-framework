package com.application.orchestrator.engine

import com.google.protobuf.ByteString
import com.orchestrator.interpreter.dsl.ExecutionPlan
import com.orchestrator.proto.ActivityResult
import com.orchestrator.proto.InterpreterWorkerResult

/**
 * Interface for the orchestrator engine, which is the central component of the orchestration system
 * responsible for managing the lifecycle of distributed workflows.
 */
interface OrchestratorEngineInterface {
    /**
     * Starts a new workflow execution.
     *
     * @param workflowName The name of the workflow to start
     * @param inputData The input data for the workflow
     * @return The ID of the created workflow instance
     * @throws IllegalArgumentException if workflowName is empty or inputData is empty
     */
    suspend fun startWorkflow(workflowName: String, inputData: ByteString): String

    /**
     * Submits execution plans to the orchestrator.
     *
     * @param executionPlans The list of execution plans to submit
     * @throws IllegalArgumentException if executionPlans is empty
     */
    suspend fun submitExecutionPlain(executionPlans: List<ExecutionPlan>)

    /**
     * Submits interpreter results for processing.
     *
     * @param interpreterResults The list of interpreter results to process
     * @throws IllegalArgumentException if interpreterResults is empty
     */
    suspend fun submitInterpreterResults(interpreterResults: List<InterpreterWorkerResult>)

    /**
     * Submits an activity result for processing.
     *
     * @param activityResult The activity result to process
     * @throws IllegalArgumentException if activityResult is invalid
     */
    suspend fun submitActivityResult(activityResult: ActivityResult)
}
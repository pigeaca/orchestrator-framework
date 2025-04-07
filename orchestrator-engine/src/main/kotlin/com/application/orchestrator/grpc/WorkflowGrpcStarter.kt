package com.application.orchestrator.grpc

import com.application.orchestrator.data.OrchestratorWorkflowData
import com.application.orchestrator.engine.OrchestratorEngine
import com.google.protobuf.ByteString
import com.orchestrator.proto.GetWorkflowResultRequest
import com.orchestrator.proto.StartWorkflowRequest
import com.orchestrator.proto.StartWorkflowResponse
import com.orchestrator.proto.WorkflowEngineServiceGrpcKt.WorkflowEngineServiceCoroutineImplBase
import com.orchestrator.proto.WorkflowResult
import org.springframework.stereotype.Service

@Service
class WorkflowGrpcStarter(
    private val orchestratorEngine: OrchestratorEngine,
    private val orchestratorWorkflowData: OrchestratorWorkflowData
) : WorkflowEngineServiceCoroutineImplBase() {

    override suspend fun startWorkflow(request: StartWorkflowRequest): StartWorkflowResponse {
        val workflowId = orchestratorEngine.startWorkflow(request.sagaName, request.inputData)
        return StartWorkflowResponse.newBuilder()
            .setSagaId(workflowId)
            .build()
    }

    override suspend fun tryGetWorkflowResult(request: GetWorkflowResultRequest): WorkflowResult {
        val workflowResponse = orchestratorWorkflowData.pollWorkflowResponse(request.sagaId)
        return WorkflowResult.newBuilder()
            .setInputData(workflowResponse ?: ByteString.EMPTY)
            .build()
    }
}
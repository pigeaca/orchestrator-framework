package com.application.orchestrator.grpc

import com.application.orchestrator.engine.OrchestratorEngine
import com.orchestrator.proto.StartWorkflowRequest
import com.orchestrator.proto.StartWorkflowResponse
import com.orchestrator.proto.WorkflowEngineServiceGrpcKt.WorkflowEngineServiceCoroutineImplBase
import org.springframework.stereotype.Service

@Service
class WorkflowGrpcStarter(private val orchestratorEngine: OrchestratorEngine): WorkflowEngineServiceCoroutineImplBase() {
    override suspend fun startWorkflow(request: StartWorkflowRequest): StartWorkflowResponse {
        val workflowId = orchestratorEngine.startWorkflow(request.sagaName, request.inputData)
        return StartWorkflowResponse.newBuilder()
            .setSagaId(workflowId)
            .build()
    }
}
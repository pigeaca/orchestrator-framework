package com.orchestrator.config

import com.orchestrator.proto.WorkflowEngineServiceGrpcKt
import com.orchestrator.starter.WorkflowStarter
import com.orchestrator.starter.impl.WorkflowStarterImpl
import io.grpc.Channel
import io.grpc.ManagedChannel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "workflow.role", name = ["mode"], havingValue = "initiator")
open class WorkflowStarterConfig {

    @Bean
    open fun workflowInitiator(workflowEngineServiceCoroutineStub: WorkflowEngineServiceGrpcKt.WorkflowEngineServiceCoroutineStub): WorkflowStarter {
        return WorkflowStarterImpl(workflowEngineServiceCoroutineStub)
    }

    @Bean
    open fun workflowStarterStub(@Qualifier("engine") channel: Channel): WorkflowEngineServiceGrpcKt.WorkflowEngineServiceCoroutineStub =
        WorkflowEngineServiceGrpcKt.WorkflowEngineServiceCoroutineStub(channel)
}
package com.orchestrator.grpc

import com.orchestrator.config.WorkflowEngineProperties
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class GrpcClientConfig {

    @Bean(value = ["engine"])
    open fun grpcManagedChannel(workflowEngineProperties: WorkflowEngineProperties
    ): ManagedChannel =
        ManagedChannelBuilder.forAddress(workflowEngineProperties.host, workflowEngineProperties.port)
            .usePlaintext()
            .build()
}
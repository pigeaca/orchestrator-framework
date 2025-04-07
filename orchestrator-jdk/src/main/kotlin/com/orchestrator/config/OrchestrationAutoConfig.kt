package com.orchestrator.config

import com.orchestrator.grpc.GrpcClientConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@AutoConfiguration
@Import(
    GrpcClientConfig::class,
    ActivityConfig::class,
    InterpreterConfig::class,
    WorkflowStarterConfig::class,
)
@EnableConfigurationProperties(WorkflowEngineProperties::class)
open class OrchestrationAutoConfig
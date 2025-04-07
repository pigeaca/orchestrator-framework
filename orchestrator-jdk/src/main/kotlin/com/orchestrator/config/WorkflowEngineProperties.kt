package com.orchestrator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workflow.engine")
data class WorkflowEngineProperties(
    var host: String = "localhost",
    var port: Int = 6565
)
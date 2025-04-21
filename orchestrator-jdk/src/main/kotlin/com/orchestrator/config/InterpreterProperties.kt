package com.orchestrator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workflow.interpreter")
class InterpreterProperties {
    var fetchDelay: Long = 100
}
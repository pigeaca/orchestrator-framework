package com.orchestrator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workflow.activity")
class ActivityProperties {
    var fetchDelay: Long = 100
}
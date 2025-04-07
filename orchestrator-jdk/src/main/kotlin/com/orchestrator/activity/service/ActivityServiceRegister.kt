package com.orchestrator.activity.service

class ActivityServiceRegister {
    private val activities: MutableMap<String, Any> = mutableMapOf()

    fun registerActivityService(name: String, service: Any) {
        activities[name] = service
    }

    fun getActivityService(qualifiedName: String): Any? = activities[qualifiedName]
}
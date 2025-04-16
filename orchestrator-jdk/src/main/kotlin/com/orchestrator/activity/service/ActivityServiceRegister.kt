package com.orchestrator.activity.service

class ActivityServiceRegister {
    private val activities: MutableMap<String, Any> = mutableMapOf()
    private val activityQueues: HashSet<String> = hashSetOf()

    fun registerActivityService(name: String, service: Any) {
        activities[name] = service
    }

    fun registerQueueActivity(queue: String) {
        activityQueues.add(queue)
    }

    fun getActivityQueue(): Set<String> = activityQueues

    fun getActivityService(qualifiedName: String): Any? = activities[qualifiedName]
}
package com.application.orchestrator.service.activity

import com.orchestrator.proto.ActivityTask

interface ActivityQueueManager {
    suspend fun init(queues: Collection<String>)
    suspend fun pollTask(queue: String): ActivityTask?
    suspend fun submitTask(task: ActivityTask)
}
package com.application.orchestrator.service.activity

import com.orchestrator.proto.ActivityResult
import com.orchestrator.proto.ActivityTask
import com.orchestrator.proto.ActivityTaskResponse

interface ActivityTaskPollingService {
    suspend fun pollTask(queue: String): ActivityTask?
    suspend fun submitResult(activityResult: ActivityResult)
}

fun ActivityTask.toResponse(): ActivityTaskResponse = ActivityTaskResponse
    .newBuilder()
    .setTask(this)
    .build()
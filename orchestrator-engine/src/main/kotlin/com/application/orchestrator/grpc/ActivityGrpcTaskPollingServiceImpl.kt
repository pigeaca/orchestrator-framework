package com.application.orchestrator.grpc

import com.application.orchestrator.service.activity.ActivityTaskPollingService
import com.application.orchestrator.service.activity.toResponse
import com.orchestrator.proto.*
import org.springframework.stereotype.Service

@Service
class ActivityGrpcTaskPollingServiceImpl(
    private val activityTaskPollingService: ActivityTaskPollingService
): ActivityTaskServiceGrpcKt.ActivityTaskServiceCoroutineImplBase() {

    override suspend fun pollTask(request: PollTaskRequest): ActivityTaskResponse {
        val activityTask = activityTaskPollingService.pollTask(request.queue)
        return activityTask?.toResponse() ?: ActivityTaskResponse.getDefaultInstance()
    }

    override suspend fun submitResult(request: ActivityResult): Empty {
        activityTaskPollingService.submitResult(activityResult = request)
        return Empty.getDefaultInstance()
    }
}
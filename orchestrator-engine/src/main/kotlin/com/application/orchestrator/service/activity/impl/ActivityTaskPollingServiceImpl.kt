package com.application.orchestrator.service.activity.impl

import com.application.orchestrator.engine.OrchestratorEngine
import com.application.orchestrator.service.activity.ActivityQueueManager
import com.application.orchestrator.service.activity.ActivityTaskPollingService
import com.orchestrator.proto.ActivityResult
import com.orchestrator.proto.ActivityTask
import org.springframework.stereotype.Service

@Service
class ActivityTaskPollingServiceImpl(
    private val orchestratorEngine: OrchestratorEngine,
    private val activityQueueManager: ActivityQueueManager
) : ActivityTaskPollingService {

    override suspend fun pollTask(queue: String): ActivityTask? {
        return activityQueueManager.pollTask(queue)
    }

    override suspend fun submitResult(activityResult: ActivityResult) {
        orchestratorEngine.submitActivityResult(activityResult)
    }
}
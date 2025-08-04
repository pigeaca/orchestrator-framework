package com.application.orchestrator.service.activity.impl

import com.application.orchestrator.engine.OrchestratorEngineInterface
import com.application.orchestrator.service.activity.ActivityQueueManager
import com.application.orchestrator.service.activity.ActivityTaskPollingService
import com.application.orchestrator.util.ValidationUtils
import com.orchestrator.proto.ActivityResult
import com.orchestrator.proto.ActivityTask
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Implementation of ActivityTaskPollingService.
 * Handles polling for activity tasks and submitting activity results.
 */
@Service
class ActivityTaskPollingServiceImpl(
    private val orchestratorEngine: OrchestratorEngineInterface,
    private val activityQueueManager: ActivityQueueManager
) : ActivityTaskPollingService {
    private val logger = LoggerFactory.getLogger(ActivityTaskPollingServiceImpl::class.java)

    /**
     * Polls for an activity task from the specified queue.
     *
     * @param queue The queue to poll from
     * @return The activity task or null if no task is available
     * @throws IllegalArgumentException if queue is empty
     */
    override suspend fun pollTask(queue: String): ActivityTask? {
        ValidationUtils.validateNotEmpty(queue, "queue")
        logger.debug("Polling for task from queue: $queue")
        return activityQueueManager.pollTask(queue)
    }

    /**
     * Submits an activity result for processing.
     *
     * @param activityResult The activity result to process
     * @throws IllegalArgumentException if activityResult is null
     */
    override suspend fun submitResult(activityResult: ActivityResult) {
        ValidationUtils.validateNotNull(activityResult, "activityResult")
        logger.debug("Submitting activity result: $activityResult")
        orchestratorEngine.submitActivityResult(activityResult)
    }
}
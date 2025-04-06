package com.application.orchestrator.service.activity.impl

import com.application.orchestrator.data.OrchestratorSagaStepData
import com.application.orchestrator.data.WorkflowLockManager
import com.application.orchestrator.engine.StepStatus
import com.application.orchestrator.service.activity.ActivityQueueManager
import com.orchestrator.proto.ActivityTask
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

@Service
class ActivityQueueManagerImpl(
    private val lockManager: WorkflowLockManager,
    private val orchestratorSagaStepData: OrchestratorSagaStepData
) : ActivityQueueManager {
    private val activityTasks = mutableMapOf<String, ArrayDeque<ActivityTask>>()

    override suspend fun init(queues: Collection<String>) {
        queues.forEach { queue -> activityTasks.computeIfAbsent(queue) {ArrayDeque<ActivityTask>() } }
    }

    override suspend fun pollTask(queue: String): ActivityTask? {
        val activityTask = activityTasks[queue]?.poll() ?: return null
        val acquired = lockManager.tryAcquire(activityTask.sagaId, "ActivityQueueManager", Duration.ofSeconds(30))
        if (!acquired) return null
        try {
            orchestratorSagaStepData.updateStepStatus(activityTask.sagaId, activityTask.stepId, StepStatus.IN_PROGRESS)
            return activityTask
        } finally {
            lockManager.release(activityTask.sagaId, "ActivityQueueManager")
        }
    }

    override suspend fun submitTask(task: ActivityTask) {
        activityTasks.computeIfPresent(task.queue) {_, queue ->
            queue.push(task)
            queue
        }
    }
}
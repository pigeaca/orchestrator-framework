package com.application.orchestrator.service.activity.impl

import com.application.orchestrator.service.activity.ActivityQueueManager
import com.orchestrator.proto.ActivityTask
import org.springframework.stereotype.Service
import java.util.*

@Service
class ActivityQueueManagerImpl : ActivityQueueManager {
    private val activityTasks = mutableMapOf<String, ArrayDeque<ActivityTask>>()

    override suspend fun init(queues: Collection<String>) {
        queues.forEach { queue -> activityTasks.computeIfAbsent(queue) {ArrayDeque<ActivityTask>() } }
    }

    override suspend fun pollTask(queue: String): ActivityTask? {
        return activityTasks[queue]?.poll()
    }

    override suspend fun submitTask(task: ActivityTask) {
        activityTasks.computeIfPresent(task.queue) {_, queue ->
            queue.push(task)
            queue
        }
    }
}
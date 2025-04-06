package com.orchestrator.activity.worker.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.ByteString
import com.orchestrator.activity.worker.ActivityProcessor
import com.orchestrator.activity.worker.ActivityWorker
import com.orchestrator.proto.ActivityResult
import com.orchestrator.proto.ActivityTask
import com.orchestrator.proto.ActivityTaskServiceGrpcKt
import com.orchestrator.proto.PollTaskRequest
import kotlinx.coroutines.*
import org.springframework.stereotype.Service

@Service
class DefaultActivityWorker(
    private val activityTaskPollingService: ActivityTaskServiceGrpcKt.ActivityTaskServiceCoroutineStub,
    private val activityProcessor: ActivityProcessor
) : ActivityWorker {
    private val objectMapper = jacksonObjectMapper()
    private var job: Job? = null

    override fun startPollingTasks(queues: List<String>) {
        this.job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (true) {
                queues.map { queue -> processAsyncQueue(queue) }.forEach { it.await() }
                delay(400)
            }
        }
    }

    private fun CoroutineScope.processAsyncQueue(queue: String) = async {
        val pollTaskRequest = PollTaskRequest.newBuilder().setQueue(queue).build()
        val activityTaskResponse = activityTaskPollingService.pollTask(pollTaskRequest)
        activityTaskResponse.task ?: return@async null
        try {
            val activityResultData = activityProcessor.processActivity(activityTaskResponse.task)
            onSuccessSubmit(activityTaskResponse.task, activityResultData, queue)
        } catch (e: Exception) {
            onErrorSubmit(activityTaskResponse.task, queue, e)
        }
    }

    private suspend fun onSuccessSubmit(activityTask: ActivityTask, activityResultData: Any?, queue: String) {
        val valueAsBytes = ByteString.copyFrom(objectMapper.writeValueAsBytes(activityResultData))
        val activityResultReport = createSuccessResultReport(activityTask, valueAsBytes, queue)
        activityTaskPollingService.submitResult(activityResultReport)
    }

    private suspend fun onErrorSubmit(activityTask: ActivityTask, queue: String, e: Exception) {
        val activityResultReport = createFailedResultReport(activityTask, e, queue)
        activityTaskPollingService.submitResult(activityResultReport)
    }

    override fun stopPollingTasks() {
        job?.cancel()
    }
}

fun createSuccessResultReport(
    activityTask: ActivityTask,
    activityResultData: ByteString,
    queue: String
): ActivityResult = ActivityResult.newBuilder()
    .setSagaId(activityTask.sagaId)
    .setStepId(activityTask.stepId)
    .setSuccess(true)
    .setStepType(activityTask.type)
    .setSagaName(activityTask.sagaName)
    .setQueue(queue)
    .setOutput(activityResultData)
    .build()

fun createFailedResultReport(
    activityTask: ActivityTask,
    e: Exception,
    queue: String
) : ActivityResult = ActivityResult.newBuilder()
    .setSagaId(activityTask.sagaId)
    .setStepId(activityTask.stepId)
    .setSuccess(false)
    .setStepType(activityTask.type)
    .setSagaName(activityTask.sagaName)
    .setQueue(queue)
    .setError(e.message)
    .build()
package com.orchestrator.activity.worker

interface ActivityWorker {
    fun startPollingTasks(queues: List<String>)
    fun stopPollingTasks()
}
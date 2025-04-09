package com.orchestrator.activity.worker

interface ActivityWorker {
    fun startPollingTasks()
    fun stopPollingTasks()
}
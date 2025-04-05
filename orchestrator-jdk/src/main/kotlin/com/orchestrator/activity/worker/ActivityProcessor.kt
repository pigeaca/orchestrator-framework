package com.orchestrator.activity.worker

import com.orchestrator.proto.ActivityTask

interface ActivityProcessor {
    suspend fun processActivity(activityTask: ActivityTask): Any?
}
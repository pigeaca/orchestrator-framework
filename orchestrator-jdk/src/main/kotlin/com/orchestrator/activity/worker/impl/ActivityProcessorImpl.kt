package com.orchestrator.activity.worker.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.orchestrator.activity.annotation.Activity
import com.orchestrator.activity.service.ActivityServiceRegister
import com.orchestrator.activity.worker.ActivityProcessor
import com.orchestrator.proto.ActivityTask
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.jvmErasure

class ActivityProcessorImpl(
    private val activityServiceRegister: ActivityServiceRegister
): ActivityProcessor {
    private val objectMapper = jacksonObjectMapper()

    override suspend fun processActivity(activityTask: ActivityTask): Any? {
        val activityService = activityServiceRegister.getActivityService(activityTask.serviceName) ?: return null

        val kFunction = activityService::class.superclasses.flatMap { it.functions }
            .firstOrNull { fn -> fn.name == activityTask.methodName && fn.findAnnotation<Activity>() != null }

        if (kFunction == null) {
            println("annotation @Activity was not found")
            return null
        }

        val parameterType = kFunction.parameters.getOrNull(1)?.type?.jvmErasure
        if (parameterType == null) {
            println("Failed to find required parameter")
            return null
        }
        if (activityTask.input.isEmpty) {
            return kFunction.callSuspend(activityService, null)
        }
        val parsedInput = objectMapper.readValue(activityTask.input.toByteArray(), parameterType.java)
        return kFunction.callSuspend(activityService, parsedInput)
    }
}
package com.orchestrator.config

import com.orchestrator.activity.service.ActivityBeanPostProcessor
import com.orchestrator.activity.service.ActivityServiceRegister
import com.orchestrator.activity.worker.ActivityProcessor
import com.orchestrator.activity.worker.ActivityWorker
import com.orchestrator.activity.worker.impl.ActivityProcessorImpl
import com.orchestrator.activity.worker.impl.DefaultActivityWorker
import com.orchestrator.proto.ActivityTaskServiceGrpcKt
import io.grpc.ManagedChannel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "workflow.role", name = ["mode"], havingValue = "activity")
open class ActivityConfig {

    @Bean
    open fun activityBeenProcessor(activityServiceRegister: ActivityServiceRegister): ActivityBeanPostProcessor {
        return ActivityBeanPostProcessor(activityServiceRegister)
    }

    @Bean
    open fun workflowActivity(
        activityTaskServiceCoroutineStub: ActivityTaskServiceGrpcKt.ActivityTaskServiceCoroutineStub,
        activityProcessor: ActivityProcessor,
        activityServiceRegister: ActivityServiceRegister
    ): ActivityWorker {
        val activityWorker = DefaultActivityWorker(
            activityTaskServiceCoroutineStub,
            activityProcessor,
            activityServiceRegister,
        )
        activityWorker.startPollingTasks()
        return activityWorker
    }

    @Bean
    open fun activityProcessor(activityServiceRegister: ActivityServiceRegister): ActivityProcessor {
        return ActivityProcessorImpl(activityServiceRegister)
    }

    @Bean
    open fun activityRegister(): ActivityServiceRegister {
        return ActivityServiceRegister()
    }

    @Bean
    open fun activityTaskServiceStub(@Qualifier("engine") channel: ManagedChannel): ActivityTaskServiceGrpcKt.ActivityTaskServiceCoroutineStub =
        ActivityTaskServiceGrpcKt.ActivityTaskServiceCoroutineStub(channel)
}
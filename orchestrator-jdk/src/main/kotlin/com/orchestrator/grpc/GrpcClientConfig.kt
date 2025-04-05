package com.orchestrator.grpc

import com.orchestrator.proto.ActivityTaskServiceGrpcKt
import com.orchestrator.proto.InterpreterWorkerServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class GrpcClientConfig {

    @Bean(value = ["engine"])
    open fun grpcManagedChannel(): ManagedChannel =
        ManagedChannelBuilder.forAddress("localhost", 8083)
            .usePlaintext()
            .build()

    @Bean
    open fun interpreterWorkerStub(@Qualifier("engine") channel: ManagedChannel): InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub =
        InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub(channel)

    @Bean
    open fun activityTaskServiceStub(@Qualifier("engine") channel: ManagedChannel): ActivityTaskServiceGrpcKt.ActivityTaskServiceCoroutineStub =
        ActivityTaskServiceGrpcKt.ActivityTaskServiceCoroutineStub(channel)

}
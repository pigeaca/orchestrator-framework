package com.application.orchestrator.config

import io.grpc.BindableService
import io.grpc.Server
import io.grpc.ServerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class GrpcServerConfig {

    @Bean
    open fun grpcServer(
        @Value("\${grpc.server.port}") port: Int,
        messageServices: List<BindableService>): Server {
        return ServerBuilder
            .forPort(port)
            .addServices(messageServices.map { it.bindService() })
            .build()
            .start()
            .also { println("✅ gRPC server started on port $port, services ${messageServices.map { it::class.simpleName }}") }
    }
}

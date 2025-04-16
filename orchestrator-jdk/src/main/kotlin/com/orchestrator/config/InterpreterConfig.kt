package com.orchestrator.config

import com.orchestrator.interpreter.service.InterpreterBeanPostProcessor
import com.orchestrator.interpreter.service.InterpreterServiceRegister
import com.orchestrator.interpreter.service.OrchestratorDefinitionProvider
import com.orchestrator.interpreter.worker.InterpreterWorker
import com.orchestrator.interpreter.worker.TaskInterpreter
import com.orchestrator.interpreter.worker.impl.DefaultInterpreterWorker
import com.orchestrator.interpreter.worker.impl.TaskInterpreterImpl
import com.orchestrator.proto.InterpreterWorkerServiceGrpcKt
import io.grpc.ManagedChannel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "workflow.role", name = ["mode"], havingValue = "interpreter")
open class InterpreterConfig {

    @Bean
    open fun workflowInterpreter(
        definitionProviders: List<OrchestratorDefinitionProvider>,
        interpreterWorkerServiceCoroutineStub: InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub,
        taskInterpreter: TaskInterpreter
    ): InterpreterWorker {
        val interpreterWorker =
            DefaultInterpreterWorker(definitionProviders, interpreterWorkerServiceCoroutineStub, taskInterpreter)
        interpreterWorker.startInterpreter()
        return interpreterWorker
    }

    @Bean
    open fun interpreterBeanPostProcessor(interpreterServiceRegister: InterpreterServiceRegister) : InterpreterBeanPostProcessor {
        return InterpreterBeanPostProcessor(interpreterServiceRegister)
    }

    @Bean
    open fun interpreterServiceRegister(): InterpreterServiceRegister {
        return InterpreterServiceRegister()
    }

    @Bean
    open fun taskInterpreter(
        interpreterServiceRegister: InterpreterServiceRegister,
        interpreterWorkerTaskPollingService: InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub
    ) : TaskInterpreter {
        return TaskInterpreterImpl(interpreterWorkerTaskPollingService, interpreterServiceRegister)
    }

    @Bean
    open fun interpreterWorkerStub(@Qualifier("engine") channel: ManagedChannel): InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub =
        InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub(channel)

}
package com.orchestrator.interpreter.worker.impl

import com.orchestrator.interpreter.dsl.OrchestrationDefinition
import com.orchestrator.interpreter.dsl.compileToExecutionPlan
import com.orchestrator.interpreter.dsl.toProto
import com.orchestrator.interpreter.worker.InterpreterWorker
import com.orchestrator.interpreter.worker.TaskInterpreter
import com.orchestrator.proto.Empty
import com.orchestrator.proto.InterpreterWorkerResultList
import com.orchestrator.proto.InterpreterWorkerServiceGrpcKt
import com.orchestrator.proto.SendExecutionPlanRequest
import kotlinx.coroutines.*


class DefaultInterpreterWorker(
    private val interpreterWorkerTaskPollingService: InterpreterWorkerServiceGrpcKt.InterpreterWorkerServiceCoroutineStub,
    private val taskInterpreter: TaskInterpreter
) : InterpreterWorker {
    private var job: Job? = null

    override fun startInterpreter(orchestrationDefinitions: List<OrchestrationDefinition<*, *>>) {
        val executionPlans = orchestrationDefinitions.map { it.compileToExecutionPlan() }
        val orchestrationDefinitionsGroup = orchestrationDefinitions.associateBy { it.name }
        this.job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            interpreterWorkerTaskPollingService.sendExecutionPlan(
                SendExecutionPlanRequest
                    .newBuilder()
                    .addAllPlans(executionPlans.map { it.toProto() })
                    .build()
            )
            while (true) {
                val interpreterWorkerTasks = interpreterWorkerTaskPollingService.pollTasks(
                    Empty.getDefaultInstance()
                )
                if (interpreterWorkerTasks.tasksList.isNotEmpty()) {
                    val interpreterWorkerResults =
                        taskInterpreter.interpreterTasks(interpreterWorkerTasks.tasksList, orchestrationDefinitionsGroup)
                    interpreterWorkerTaskPollingService.submitResults(InterpreterWorkerResultList
                        .newBuilder()
                        .addAllResults(interpreterWorkerResults)
                        .build())
                }
                delay(400)
            }
        }
    }

    override fun stopInterpreter() {
        this.job?.cancel()
    }
}
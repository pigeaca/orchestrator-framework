package com.orchestrator.interpreter.dsl

import kotlin.reflect.KClass
import kotlin.reflect.KFunction2
import kotlin.reflect.KSuspendFunction2

// ========== Core Types ==========

class ActivityCall<Input, Output>(
    val serviceName: String,
    val methodName: String,
    val input: suspend StepContext.() -> Input?,
)

data class Step<Input, Output>(
    val type: String,
    val queue: String,
    val call: ActivityCall<Input, Output>,
    val rollbackStep: MutableList<Step<*, *>>
)

data class OrchestrationDefinition<Request : Any, Response : Any>(
    val name: String,
    val workflowRequest: KClass<Request>,
    val workflowResponse: KClass<Response>,
    val steps: List<Step<*, *>>,
    val version: String = "1.0.0",

    val onComplete: (suspend StepContext.() -> Response)? = null,
    val onFailure: (suspend StepContext.() -> Response)? = null
)

interface ResultProvider {
    suspend fun <T : Any> resultOf(stepType: String, requestType: KClass<T>): T?
    suspend fun <T : Any> useRequest(requestType: KClass<T>): T
}

class StepContext(val resultProvider: ResultProvider) {
    suspend inline fun <reified Result : Any> resultOf(step: Step<*, Result>): Result? =
        resultProvider.resultOf(step.type, Result::class)

    suspend inline fun <reified Result : Any> useRequest(definitionContext: DefinitionContext<Result, *>): Result {
        return resultProvider.useRequest(Result::class)
    }
}


// ========== DSL Entry ==========

fun <Request : Any, Response : Any> orchestration(
    name: String,
    workflowRequest: KClass<Request>,
    workflowResponse: KClass<Response>,
    version: String = "1.0.0",
    block: OrchestrationBuilder<Request, Response>.(context: DefinitionContext<Request, Response>) -> Unit
): OrchestrationDefinition<Request, Response> {
    val builder = OrchestrationBuilder(name, workflowRequest, workflowResponse, version)
    builder.block(DefinitionContext(name, workflowRequest, workflowResponse))
    val orchestrationDefinition = builder.build()
    return orchestrationDefinition
}

data class DefinitionContext<Request : Any, Response : Any>(
    val name: String,
    val workflowRequest: KClass<Request>,
    val workflowResponse: KClass<Response>,
)

class OrchestrationBuilder<Request : Any, Response : Any>(
    private val name: String,
    private val workflowRequest: KClass<Request>,
    private val workflowResponse: KClass<Response>,
    private val version: String = "1.0.0"
) {
    private val steps = mutableListOf<Step<*, *>>()

    private var completeBlock: (suspend StepContext.() -> Response)? = null
    private var failureBlock: (suspend StepContext.() -> Response)? = null

    fun onComplete(block: suspend StepContext.() -> Response) {
        this.completeBlock = block
    }

    fun onFailure(block: suspend StepContext.() -> Response) {
        this.failureBlock = block
    }

    fun <Input, Output> step(
        type: String,
        queue: String,
        call: ActivityCall<Input, Output>,
        rollbackStep: (step: Step<Input, Output>) -> Step<*, *>
    ): Step<Input, Output> {
        val step = Step(type, queue, call, mutableListOf())
        val rollback = rollbackStep.invoke(step)
        step.rollbackStep.add(rollback)
        steps += step
        return step
    }

    fun <Input, Output> step(
        type: String,
        queue: String,
        call: ActivityCall<Input, Output>,
        rollbackStepRefs: List<(step: Step<Input, Output>) -> Step<*, *>>
    ): Step<Input, Output> {
        val step = Step(type, queue, call, mutableListOf())
        val rollbacks = rollbackStepRefs.map { it.invoke(step) }
        step.rollbackStep.addAll(rollbacks)
        steps += step
        return step
    }

    fun build(): OrchestrationDefinition<Request, Response> = OrchestrationDefinition(
        name,
        workflowRequest,
        workflowResponse,
        steps,
        version,
        onComplete = completeBlock,
        onFailure = failureBlock
    )
}

fun <Input, Output> rollbackStep(
    type: String,
    queue: String,
    call: ActivityCall<Input, Output>
): Step<Input, Output> {
    val step = Step(type, queue, call, mutableListOf())
    return step
}

// ========== Activity Builder ==========

class ActivityBuilder<Activity : Any>(private val service: KClass<Activity>) {
    fun <Input, Output> call(
        method: KFunction2<Activity, Input, Output>,
        input: suspend StepContext.() -> Input?
    ): ActivityCall<Input, Output> {
        return ActivityCall(serviceName = service.qualifiedName ?: "", methodName = method.name, input = input)
    }

    fun <Input, Output> suspendCall(
        method: KSuspendFunction2<Activity, Input, Output>,
        input: suspend StepContext.() -> Input?
    ): ActivityCall<Input, Output> {
        return ActivityCall(serviceName = service.qualifiedName ?: "", methodName = method.name, input = input)
    }
}

// === Worker === //
suspend fun <Input, Output> getActivityPayload(activity: ActivityCall<Input, Output>, context: StepContext): Input? {
    return activity.input.invoke(context)
}

fun <Activity : Any> activity(service: KClass<Activity>): ActivityBuilder<Activity> = ActivityBuilder(service)


// === Plans === //

fun OrchestrationDefinition<*, *>.buildStepMap(): Map<String, Step<*, *>> {
    return steps
        .flatMap { step -> listOf(step) + step.rollbackStep }
        .associateBy { it.type }
}

fun OrchestrationDefinition<*, *>.compileToExecutionPlan(): ExecutionPlan {
    val executionSteps = mutableListOf<ExecutionStep>()

    val allSteps = steps

    for ((index, step) in allSteps.withIndex()) {
        val dependsOn = allSteps.getOrNull(index - 1)?.type?.let { listOf(it) } ?: emptyList()
        val rollbackIds = step.rollbackStep.map { it.type }

        executionSteps += ExecutionStep(
            sagaType = name,
            isRollback = false,
            stepType = step.type,
            queue = step.queue,
            dependencies = dependsOn,
            rollbackStepIds = rollbackIds
        )
    }

    val reverseSteps = allSteps.reversed()

    for ((index, step) in reverseSteps.withIndex()) {
        val rollback = step.rollbackStep.firstOrNull() ?: continue
        val previousRollback = reverseSteps.getOrNull(index - 1)?.rollbackStep?.firstOrNull()

        val dependsOn = previousRollback?.type?.let { listOf(it) } ?: emptyList()

        executionSteps += ExecutionStep(
            sagaType = name,
            isRollback = true,
            stepType = rollback.type,
            queue = rollback.queue,
            dependencies = dependsOn,
            rollbackStepIds = emptyList()
        )
    }

    return ExecutionPlan(
        name = name,
        steps = executionSteps,
        version = version
    )
}

data class ExecutionPlan(
    val name: String,
    val steps: List<ExecutionStep>,
    val version: String = "1.0.0"
)

fun ExecutionPlan.toProto(): com.orchestrator.proto.ExecutionPlan {
    val builder = com.orchestrator.proto.ExecutionPlan.newBuilder()
        .setName(name)
        .addAllSteps(steps.map {
            com.orchestrator.proto.ExecutionStep
                .newBuilder()
                .setQueue(it.queue)
                .setIsRollback(it.isRollback)
                .setSagaType(it.sagaType)
                .setStepType(it.stepType)
                .addAllDependencies(it.dependencies)
                .addAllRollbackStepIds(it.rollbackStepIds)
                .build()
        })
    
    // Use reflection to set the version field if the method exists
    try {
        val setVersionMethod = builder.javaClass.getMethod("setVersion", String::class.java)
        setVersionMethod.invoke(builder, version)
    } catch (e: Exception) {
        // If the method doesn't exist yet, log a warning
        println("Warning: setVersion method not found in ExecutionPlan.Builder. The version field will not be set.")
    }
    
    return builder.build()
}

data class ExecutionStep(
    val stepType: String,
    val isRollback: Boolean = false,
    val sagaType: String,
    val queue: String,
    val dependencies: List<String>,
    val rollbackStepIds: List<String>
)
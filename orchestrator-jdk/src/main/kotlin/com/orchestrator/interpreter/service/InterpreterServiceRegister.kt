package com.orchestrator.interpreter.service

import org.springframework.stereotype.Component
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction

@Component
class InterpreterServiceRegister {
    private val listeners = mutableMapOf<String, SuspendListener>()

    fun register(workflow: String, bean: Any, method: Method) {
        val kFunction = method.kotlinFunction
            ?: error("Method $method is not a Kotlin function")
        listeners[workflow] = SuspendListener(bean, kFunction)
    }

    suspend fun invoke(workflow: String, argument: Any?) {
        val listener = listeners[workflow] ?: return
        listener.function.callSuspend(listener.bean, argument)
    }

    private data class SuspendListener(
        val bean: Any,
        val function: KFunction<*>
    )
}
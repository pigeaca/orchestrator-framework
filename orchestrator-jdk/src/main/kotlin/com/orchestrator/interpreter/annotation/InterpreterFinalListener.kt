package com.orchestrator.interpreter.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class InterpreterFinalListener(val workflow: String)
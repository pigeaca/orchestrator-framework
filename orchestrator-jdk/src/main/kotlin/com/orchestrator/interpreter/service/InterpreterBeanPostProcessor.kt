package com.orchestrator.interpreter.service

import com.orchestrator.interpreter.annotation.InterpreterFinalListener
import org.springframework.aop.framework.AopProxyUtils
import org.springframework.beans.factory.config.BeanPostProcessor

class InterpreterBeanPostProcessor(
    private val interpreterServiceRegister: InterpreterServiceRegister
) : BeanPostProcessor {

    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        val clazz = AopProxyUtils.ultimateTargetClass(bean)

        clazz.declaredMethods.forEach { method ->
            val annotation = method.getAnnotation(InterpreterFinalListener::class.java)
            if (annotation != null) {
                val workflow = annotation.workflow
                method.isAccessible = true
                interpreterServiceRegister.register(workflow, bean, method)
            }
        }
        return bean
    }
}
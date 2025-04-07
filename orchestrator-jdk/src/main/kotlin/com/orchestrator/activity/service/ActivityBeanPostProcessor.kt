package com.orchestrator.activity.service

import com.orchestrator.activity.annotation.ActivityService
import org.springframework.aop.framework.AopProxyUtils
import org.springframework.beans.factory.config.BeanPostProcessor

class ActivityBeanPostProcessor(private val activityServiceRegister: ActivityServiceRegister) : BeanPostProcessor {
    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        val clazz = AopProxyUtils.ultimateTargetClass(bean)
        clazz.interfaces.forEach { iface ->
            iface.getAnnotation(ActivityService::class.java)?.let {
                activityServiceRegister.registerActivityService(iface.name, bean)
            }
        }
        return bean
    }
}
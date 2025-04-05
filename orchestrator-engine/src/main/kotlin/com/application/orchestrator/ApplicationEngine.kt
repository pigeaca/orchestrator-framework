package com.application.orchestrator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class ApplicationEngine

fun main(args: Array<String>) {
    runApplication<ApplicationEngine>(*args)
}
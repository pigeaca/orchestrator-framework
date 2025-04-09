plugins {
    kotlin("jvm") version "1.9.23"
}

group = "com.orchestrator.framework"
version = "0.0.6"

repositories {
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
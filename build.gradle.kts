plugins {
    kotlin("jvm") version "1.9.23"
    id("io.gitlab.arturbosch.detekt") version "1.23.5"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "com.orchestrator.framework"
version = "0.1.1"

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

// Apply ktlint and detekt to all subprojects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    
    // KtLint configuration
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
    
    // Detekt configuration
    detekt {
        buildUponDefaultConfig = true
        allRules = false
        config = files("${project.rootDir}/config/detekt/detekt.yml")
        baseline = file("${project.rootDir}/config/detekt/baseline.xml")
        
        reports {
            html.required.set(true)
            xml.required.set(true)
            txt.required.set(true)
            sarif.required.set(true)
        }
    }
    
    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.5")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "orchestrator-framework"

include("orchestrator-jdk")
include("orchestrator-engine")

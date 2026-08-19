plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dispatch"

include("services:order-service")
include("services:payment-service")

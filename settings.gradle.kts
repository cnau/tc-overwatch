pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Enables Gradle toolchain auto-provisioning — downloads a JDK matching the
    // toolchain spec when one isn't installed locally. 1.0.0+ is required for
    // Gradle 9.x; earlier versions reference JvmVendorSpec.IBM_SEMERU which
    // was renamed in Gradle 9.5 and fails at JDK-provisioning time on CI.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tc-overwatch"

include(
    ":server",
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

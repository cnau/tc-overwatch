pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Enables Gradle toolchain auto-provisioning — downloads a JDK matching the
    // toolchain spec (Java 21) when one isn't installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
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

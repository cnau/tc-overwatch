// Root build file — applies cross-module conventions but no plugins by itself.
// Module-specific configuration lives in proto/build.gradle.kts and server/build.gradle.kts.

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.springBoot) apply false
    alias(libs.plugins.springDepMgmt) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    group = "com.tcoverwatch"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

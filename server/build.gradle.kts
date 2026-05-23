// :server — the Spring Boot application. gRPC + HTTP server, JPA persistence,
// Liquibase migrations, Kotlin extension-function boundary mappers, feature-by-package layout.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDepMgmt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

java {
    toolchain {
        // Spring Boot 4 requires Java 21+. Using 23 because it's what's installed locally
        // and satisfies the requirement; CI / Docker images may pin to 21 specifically.
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

kotlin {
    jvmToolchain(23)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// Spring Boot Cloud Native Buildpacks — produces an OCI image without writing a Dockerfile.
// Used by CI in the build-server pipeline; runs locally via `./gradlew :server:bootBuildImage`.
springBoot {
    buildInfo()
}

dependencies {
    // Generated proto stubs (Kotlin/Java gRPC types live here)
    implementation(project(":proto"))

    // Spring Boot starters (versions managed by the BOM)
    implementation(libs.springBoot.starter.web)
    implementation(libs.springBoot.starter.data.jpa)
    implementation(libs.springBoot.starter.actuator)
    implementation(libs.springBoot.starter.validation)

    // Spring-grpc (official Spring starter, replaces net.devh). The `server` starter speaks
    // native gRPC over HTTP/2 (port 9090, grpcurl-compatible). The `server-web` starter speaks
    // gRPC-Web over Spring MVC's HTTP/1.1 server (port 8080, browser-compatible). Same proto
    // handlers serve both — exactly the architecture.md target.
    implementation(platform(libs.springGrpc.dependencies.bom))
    implementation(libs.springGrpc.server.starter)
    implementation(libs.springGrpc.server.web.starter)
    implementation(libs.grpc.services) // reflection, health checks
    runtimeOnly(libs.grpc.netty.shaded)

    // Persistence
    runtimeOnly(libs.postgres.jdbc)
    implementation(libs.liquibase.core)
    implementation(libs.liquibase.groovyDsl)
    implementation(libs.springBoot.liquibase) // SB4 moved Liquibase autoconfig to its own module

    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)

    // Tests
    testImplementation(libs.springBoot.starter.test) {
        exclude(group = "org.mockito") // we use MockK
    }
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.assertk)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktlint {
    version.set("1.4.1")
    filter {
        exclude { it.file.path.contains("/generated/") }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.files("detekt.yml"))
}

// detekt 1.23.x embeds Kotlin 2.0.x; running under Kotlin 2.2 fails with
// "compiled with Kotlin 2.0.21 but currently running with 2.2.0". detekt 2.0.0
// (which embeds 2.2) is not yet released. Disable detekt tasks until it ships;
// ktlint covers formatting in the meantime. Re-enable by deleting this block.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { enabled = false }
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { enabled = false }

// :server — the Spring Boot application. HTTP/JSON API (Spring MVC + Jackson), JPA persistence,
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
    // Spring Boot starters (versions managed by the BOM)
    implementation(libs.springBoot.starter.web)
    implementation(libs.springBoot.starter.data.jpa)
    implementation(libs.springBoot.starter.actuator)
    implementation(libs.springBoot.starter.validation)
    implementation(libs.springBoot.starter.security)
    // JWT encoder/decoder beans. Pulled in for the stub auth path; the same module
    // covers Google OAuth resource-server validation when real OAuth lands.
    implementation(libs.springBoot.starter.oauth2.resource.server)

    // OpenAPI 3 spec + Swagger UI. Auto-walks @RestControllers — no annotations required
    // on existing code. See docs/claude/spring-boot.md § OpenAPI for the field-doc convention.
    implementation(libs.springdoc.openapi.webmvc.ui)

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

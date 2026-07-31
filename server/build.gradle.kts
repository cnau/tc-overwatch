// :server — the Spring Boot application. HTTP/JSON API (Spring MVC + Jackson), JPA persistence,
// Kotlin extension-function boundary mappers, feature-by-package layout.
// Liquibase lives in the separate `migrate` Docker image — not on this classpath.

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

// Embed build metadata in the JAR so /actuator/info can report version + commit.
// The runtime image (server/Dockerfile) copies the bootJar produced here.
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
    // JWT encoder/decoder beans for the HS256 session token (our own, per-session).
    implementation(libs.springBoot.starter.oauth2.resource.server)
    // OAuth2 client — Google sign-in (and any future IdP added by config alone).
    implementation(libs.springBoot.starter.oauth2.client)
    // AOP for TenantBindingAspect. SB4 dropped starter-aop; aspectjweaver + transitive spring-aop is the equivalent.
    implementation(libs.aspectjweaver)

    // OpenAPI 3 spec + Swagger UI. Auto-walks @RestControllers — no annotations required
    // on existing code. See the `backend-feature` skill § OpenAPI for the known gaps.
    implementation(libs.springdoc.openapi.webmvc.ui)

    // Persistence
    runtimeOnly(libs.postgres.jdbc)

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

// detekt 1.23.8 is compiled against Kotlin 2.0.21 and refuses to run under 2.2.0. Pinning
// only detekt's own configuration keeps the project on 2.2 — this classpath is separate
// from compileKotlin's. Drop this block when detekt 2.0.0 ships (it embeds 2.2).
configurations.matching { it.name == "detekt" }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.detektEmbeddedKotlin.get())
        }
    }
}

// detekt's embedded 2.0.21 compiler rejects --jvm-target 23, which the toolchain above sets.
// 21 is Spring Boot 4's floor and what CI provisions; detekt only parses, so this never
// reaches bytecode.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
}

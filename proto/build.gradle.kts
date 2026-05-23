// :proto — protobuf definitions + generated Kotlin/Java stubs.
// Backend modules depend on this; the frontend uses Buf to generate TypeScript separately.

import com.google.protobuf.gradle.id

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
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
}

dependencies {
    api(libs.protobuf.kotlin)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.grpc.kotlin.stub)
    api(libs.kotlin.stdlib)
    compileOnly(libs.javax.annotation.api)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpcJava.get()}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpcKotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}

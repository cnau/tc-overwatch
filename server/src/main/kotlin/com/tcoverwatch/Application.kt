package com.tcoverwatch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Application

@Suppress("SpreadOperator") // Spring Boot's Kotlin entry point; the array copy is a one-time startup cost.
fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

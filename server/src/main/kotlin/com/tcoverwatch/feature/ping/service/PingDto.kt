package com.tcoverwatch.feature.ping.service

import java.time.Instant
import java.util.UUID

data class PingDto(
    val message: String,
    val receivedAt: Instant,
    val id: UUID? = null,
)

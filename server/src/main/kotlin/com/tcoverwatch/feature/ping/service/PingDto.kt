package com.tcoverwatch.feature.ping.service

import java.time.Instant

data class PingDto(
    val message: String,
    val receivedAt: Instant,
    val id: Long? = null,
)

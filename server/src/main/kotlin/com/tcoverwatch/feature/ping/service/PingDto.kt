package com.tcoverwatch.feature.ping.service

import java.time.Instant

// Service-layer DTOs. Carry data between controller and DAO; never contain JPA entities,
// never contain proto types.

data class ServicePingRequest(
    val message: String,
    val receivedAt: Instant,
)

data class ServicePingResponse(
    val id: Long,
    val echo: String,
    val receivedAt: Instant,
)

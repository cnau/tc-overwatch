package com.tcoverwatch.feature.ping.persistence

import com.tcoverwatch.feature.ping.service.ServicePingRequest
import com.tcoverwatch.feature.ping.service.ServicePingResponse

// Entity boundary: service DTO <-> PingEntity. Plain Kotlin extension functions per the
// pinned mapper convention (docs/architecture.md § Kotlin extension-function mappers).
// Hibernate entities never escape this package.

internal fun ServicePingRequest.toEntity(): PingEntity =
    PingEntity(
        message = message,
        receivedAt = receivedAt,
    )

internal fun PingEntity.toServiceResponse(): ServicePingResponse =
    ServicePingResponse(
        id = requireNotNull(id) { "PingEntity must have an id when mapping to ServicePingResponse" },
        echo = message,
        receivedAt = receivedAt,
    )

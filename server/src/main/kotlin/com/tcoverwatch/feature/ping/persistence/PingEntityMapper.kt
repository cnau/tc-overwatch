package com.tcoverwatch.feature.ping.persistence

import com.tcoverwatch.feature.ping.service.PingDto

internal fun PingDto.toEntity(): Ping =
    Ping(
        message = message,
        receivedAt = receivedAt,
    )

internal fun Ping.toDto(): PingDto =
    PingDto(
        id = requireNotNull(id) { "Ping must have an id when mapping to PingDto" },
        message = message,
        receivedAt = receivedAt,
    )

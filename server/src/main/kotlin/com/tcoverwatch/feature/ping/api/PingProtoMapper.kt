package com.tcoverwatch.feature.ping.api

import com.tcoverwatch.feature.ping.service.ServicePingRequest
import com.tcoverwatch.feature.ping.service.ServicePingResponse
import com.tcoverwatch.v1.PingRequest
import com.tcoverwatch.v1.PingResponse
import java.time.Instant

// Proto boundary: proto wire types <-> service DTOs. Plain Kotlin extension functions per
// the pinned mapper convention (docs/architecture.md § Kotlin extension-function mappers).

internal fun PingRequest.toServiceRequest(now: Instant = Instant.now()): ServicePingRequest =
    ServicePingRequest(
        message = message,
        receivedAt = now,
    )

internal fun ServicePingResponse.toProtoResponse(): PingResponse =
    PingResponse.newBuilder()
        .setId(id)
        .setEcho(echo)
        .setServerReceivedAt(receivedAt.toString())
        .build()

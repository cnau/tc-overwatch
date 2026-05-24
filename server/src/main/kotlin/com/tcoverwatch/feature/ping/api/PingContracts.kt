package com.tcoverwatch.feature.ping.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/** Scaffold smoke-test request — round-trips a single string through the layered stack. */
data class PingRequest(
    /** Free-form message; echoed back verbatim in the response. */
    @field:NotBlank
    @field:Size(max = 1024)
    val message: String,
)

/** Scaffold smoke-test response — proves DB write + server time + UUID assignment. */
data class PingResponse(
    /** Verbatim copy of the request `message`, written through the persistence layer. */
    val echo: String,
    /** Server-side receipt timestamp, ISO-8601 UTC. */
    val serverReceivedAt: String,
    /** DB-assigned UUID of the persisted ping_log row. */
    val id: UUID,
)

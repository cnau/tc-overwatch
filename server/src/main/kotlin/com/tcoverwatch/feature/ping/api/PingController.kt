package com.tcoverwatch.feature.ping.api

import com.tcoverwatch.feature.ping.service.PingDto
import com.tcoverwatch.feature.ping.service.PingService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/ping", produces = [MediaType.APPLICATION_JSON_VALUE])
class PingController(
    private val pingService: PingService,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun ping(
        @Valid @RequestBody request: PingRequest,
    ): PingResponse = pingService.ping(request.toDto()).toResponse()
}

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

internal fun PingRequest.toDto(now: Instant = Instant.now()): PingDto = PingDto(message = message, receivedAt = now)

internal fun PingDto.toResponse(): PingResponse =
    PingResponse(
        echo = message,
        serverReceivedAt = receivedAt.toString(),
        id = requireNotNull(id) { "PingDto must have an id when mapping to PingResponse" },
    )

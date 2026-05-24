package com.tcoverwatch.feature.ping.api

import com.tcoverwatch.feature.ping.service.PingDto
import com.tcoverwatch.feature.ping.service.PingService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/ping", produces = [MediaType.APPLICATION_JSON_VALUE])
class PingController(
    private val pingService: PingService,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun ping(
        @Valid @RequestBody request: PingRequest,
    ): PingResponse = pingService.ping(request.toDto()).toResponse()
}

internal fun PingRequest.toDto(now: Instant = Instant.now()): PingDto = PingDto(message = message, receivedAt = now)

internal fun PingDto.toResponse(): PingResponse =
    PingResponse(
        echo = message,
        serverReceivedAt = receivedAt.toString(),
        id = requireNotNull(id) { "PingDto must have an id when mapping to PingResponse" },
    )

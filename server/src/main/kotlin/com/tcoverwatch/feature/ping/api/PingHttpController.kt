package com.tcoverwatch.feature.ping.api

import com.tcoverwatch.feature.ping.service.PingService
import com.tcoverwatch.feature.ping.service.ServicePingRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

// REST controller — wire-format entry point for Ping. Spring MVC + Jackson.
//
// Responsibilities per layering rules:
//   - Convert request DTO -> service DTO (via the toServiceRequest extension).
//   - Apply shape validation via Jakarta Bean Validation (@Valid).
//   - Delegate business logic to PingService.
//   - Convert service DTO -> response DTO (via the toResponse extension).
//   - Never returns or accepts JPA entities — those are confined to persistence.
@RestController
@RequestMapping("/api/ping", produces = [MediaType.APPLICATION_JSON_VALUE])
class PingHttpController(
    private val pingService: PingService,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun ping(
        @Valid @RequestBody request: PingApiRequest,
    ): PingApiResponse {
        val serviceResponse = pingService.ping(request.toServiceRequest())
        return serviceResponse.toResponse()
    }
}

// API DTOs live next to the controller. Validation lives here as Jakarta annotations.
data class PingApiRequest(
    @field:NotBlank
    @field:Size(max = MAX_MESSAGE_LENGTH)
    val message: String,
) {
    companion object {
        const val MAX_MESSAGE_LENGTH = 1024
    }
}

data class PingApiResponse(
    val echo: String,
    val serverReceivedAt: String,
    val id: Long,
)

// API <-> service-DTO extension mappers per docs/architecture.md § mappers.
internal fun PingApiRequest.toServiceRequest(now: Instant = Instant.now()): ServicePingRequest =
    ServicePingRequest(message = message, receivedAt = now)

internal fun com.tcoverwatch.feature.ping.service.ServicePingResponse.toResponse(): PingApiResponse =
    PingApiResponse(
        echo = echo,
        serverReceivedAt = receivedAt.toString(),
        id = id,
    )

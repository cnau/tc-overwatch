package com.tcoverwatch.feature.auth.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class MeResponse(
    val email: String,
    val userId: UUID?,
    val tenantId: UUID?,
)

data class CreateInvitationRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)

data class InvitationResponse(
    val id: UUID,
    val email: String,
    val token: UUID,
    val createdAt: Instant,
)

data class RlsProbeResponse(
    val tenantBound: Boolean,
    val email: String?,
    val userId: UUID?,
)

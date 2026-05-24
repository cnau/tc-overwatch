package com.tcoverwatch.feature.auth.service

import java.time.Instant
import java.util.UUID

data class InvitationDto(
    val id: UUID,
    val email: String,
    val token: UUID,
    val createdAt: Instant,
)

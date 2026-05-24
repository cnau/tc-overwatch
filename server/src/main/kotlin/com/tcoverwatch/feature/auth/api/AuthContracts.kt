package com.tcoverwatch.feature.auth.api

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class MeResponse(
    val email: String,
    val userId: UUID?,
    val tenantId: UUID?,
)

data class LoginResponse(
    val token: String,
    val user: MeResponse,
)

data class DevLoginRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)

package com.tcoverwatch.feature.auth.service

import java.util.UUID

data class SignInResult(
    val token: String,
    val email: String,
    val userId: UUID,
    val tenantId: UUID,
)

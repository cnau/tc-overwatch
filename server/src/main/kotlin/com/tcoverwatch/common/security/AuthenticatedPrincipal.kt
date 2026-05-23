package com.tcoverwatch.common.security

import java.util.UUID

data class AuthenticatedPrincipal(
    val email: String,
    val userId: UUID? = null,
    val tenantId: UUID? = null,
)

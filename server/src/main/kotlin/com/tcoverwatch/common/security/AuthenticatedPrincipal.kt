package com.tcoverwatch.common.security

import java.util.UUID

// What the SecurityContext.authentication.principal holds after the JWT filter
// fires. userId / tenantId are null in the stub-auth path (no user record exists
// until invitation acceptance); they get populated once real OAuth + invitation
// flow are wired (PR 3 of #18).
data class AuthenticatedPrincipal(
    val email: String,
    val userId: UUID? = null,
    val tenantId: UUID? = null,
)

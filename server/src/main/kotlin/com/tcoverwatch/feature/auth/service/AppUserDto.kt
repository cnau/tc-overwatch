package com.tcoverwatch.feature.auth.service

import java.util.UUID

data class AppUserDto(
    val id: UUID,
    val email: String,
    val tenantId: UUID,
)

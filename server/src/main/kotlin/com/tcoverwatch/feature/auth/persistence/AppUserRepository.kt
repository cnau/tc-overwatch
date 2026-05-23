package com.tcoverwatch.feature.auth.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AppUserRepository : JpaRepository<AppUser, UUID> {
    // tenant_id filter is implicit via RLS — never hand-filter on it. See architecture.md § Multi-tenancy.
    fun findByEmail(email: String): AppUser?
}

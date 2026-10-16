package com.tcoverwatch.feature.auth.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AppUserRepository : JpaRepository<AppUser, UUID> {
    // Cross-tenant, via a SECURITY DEFINER function that bypasses RLS: sign-in runs before
    // any tenant context exists. Don't call it from anywhere else.
    @Query(value = "SELECT * FROM tco.find_app_user_by_email(:email)", nativeQuery = true)
    fun findByEmailCrossTenant(email: String): AppUser?
}

package com.tcoverwatch.feature.auth.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AppUserRepository : JpaRepository<AppUser, UUID> {
    // Cross-tenant lookup for the auth gate, before any tenant context is set.
    // Routes through a SECURITY DEFINER function that bypasses RLS. Only callable
    // from contexts that genuinely need to find a user across tenants (sign-in flow).
    @Query(value = "SELECT * FROM tco.find_app_user_by_email(:email)", nativeQuery = true)
    fun findByEmailCrossTenant(email: String): AppUser?
}

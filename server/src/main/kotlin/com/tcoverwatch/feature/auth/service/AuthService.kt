package com.tcoverwatch.feature.auth.service

import com.tcoverwatch.common.exception.FailedPreconditionException
import com.tcoverwatch.common.exception.InvitationRequiredException
import com.tcoverwatch.common.security.JwtService
import com.tcoverwatch.feature.auth.persistence.AppUser
import com.tcoverwatch.feature.auth.persistence.AppUserRepository
import com.tcoverwatch.feature.auth.persistence.Invitation
import com.tcoverwatch.feature.auth.persistence.InvitationRepository
import com.tcoverwatch.feature.tenant.persistence.Tenant
import com.tcoverwatch.feature.tenant.persistence.TenantRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    @Value("\${auth.signup-mode}") private val signupMode: SignupMode,
    private val invitationRepository: InvitationRepository,
    private val appUserRepository: AppUserRepository,
    private val tenantRepository: TenantRepository,
    private val jwtService: JwtService,
    @PersistenceContext private val entityManager: EntityManager,
) {
    @Transactional
    fun signIn(email: String): SignInResult {
        // Normalize at the boundary — DB CHECK constraints enforce lowercase storage,
        // and case-insensitive sign-in is the universal user expectation.
        val normalizedEmail = email.lowercase()
        // Returning user: cross-tenant lookup (no tenant context yet pre-auth);
        // RLS-scoped findByEmail would hide them.
        appUserRepository.findByEmailCrossTenant(normalizedEmail)?.let { existing ->
            return existing.toSignInResult()
        }
        return when (signupMode) {
            SignupMode.INVITATION -> acceptInvitationOrReject(normalizedEmail)
            SignupMode.OPEN -> provisionNewTenant(normalizedEmail, fromInvitation = null)
            SignupMode.PAID -> throw FailedPreconditionException("Paid sign-up is not yet implemented")
        }
    }

    private fun acceptInvitationOrReject(email: String): SignInResult {
        val invitation =
            invitationRepository.findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtAsc(email)
                ?: throw InvitationRequiredException("Sign-up is currently invitation-only")
        return provisionNewTenant(email, fromInvitation = invitation)
    }

    private fun provisionNewTenant(
        email: String,
        fromInvitation: Invitation?,
    ): SignInResult {
        val tenant = tenantRepository.save(Tenant())
        val tenantId = requireNotNull(tenant.id) { "tenant id missing after save() — @GeneratedValue should populate it" }
        // Bind app.tenant_id for the rest of this transaction so RLS's WITH CHECK
        // accepts the app_user INSERT. set_config(.., true) is transaction-scoped —
        // released at commit/rollback. Hibernate auto-flushes the queued tenant
        // INSERT before this native query runs, so the tenant row exists by the
        // time the config is set, and the config remains in effect through commit
        // when the user INSERT actually fires.
        entityManager
            .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
            .setParameter("tenantId", tenantId.toString())
            .singleResult
        val user = appUserRepository.save(AppUser(tenantId = tenantId, email = email))
        // JPA dirty-checking writes these field changes at commit — no explicit save() needed.
        fromInvitation?.also {
            it.acceptedAt = Instant.now()
            it.tenantId = tenantId
        }
        return user.toSignInResult()
    }

    private fun AppUser.toSignInResult(): SignInResult {
        val userId = requireNotNull(id) { "AppUser must have an id after save" }
        return SignInResult(
            token = jwtService.mint(email = email, userId = userId, tenantId = tenantId),
            email = email,
            userId = userId,
            tenantId = tenantId,
        )
    }
}

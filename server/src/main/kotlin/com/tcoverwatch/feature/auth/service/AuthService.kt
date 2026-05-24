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
import java.util.UUID

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
        // Normalize at the boundary — DB CHECK enforces lowercase; sign-in is case-insensitive.
        val normalizedEmail = email.lowercase()
        // Serialize concurrent first sign-ins; otherwise two races both provision
        // tenants before uq_app_user_email catches the user INSERT, leaving an orphan.
        entityManager
            .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:email))")
            .setParameter("email", normalizedEmail)
            .singleResult
        // Cross-tenant lookup: no tenant context yet pre-auth; RLS-scoped findByEmail would hide.
        appUserRepository.findByEmailCrossTenant(normalizedEmail)?.let { existing ->
            return existing.toSignInResult()
        }
        return when (signupMode) {
            SignupMode.INVITATION -> acceptInvitationOrReject(normalizedEmail)
            SignupMode.OPEN -> provisionNewTenant(normalizedEmail, fromInvitation = null)
            SignupMode.PAID -> throw FailedPreconditionException("Paid sign-up is not yet implemented")
        }
    }

    @Transactional
    fun createInvitation(email: String): InvitationDto = invitationRepository.save(Invitation(email = email.lowercase())).toDto()

    @Transactional(readOnly = true)
    fun findCurrentAppUser(userId: UUID): AppUserDto? = appUserRepository.findById(userId).orElse(null)?.toDto()

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
        // Flush so subsequent ops see the tenant — don't rely on FlushMode.AUTO.
        entityManager.flush()
        // Pre-auth: no principal yet, so bind app.tenant_id ourselves for the app_user RLS WITH CHECK.
        entityManager
            .createNativeQuery("SELECT set_config('app.tenant_id', :tenantId, true)")
            .setParameter("tenantId", tenantId.toString())
            .singleResult
        val user = appUserRepository.save(AppUser(tenantId = tenantId, email = email))
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

    private fun AppUser.toDto(): AppUserDto =
        AppUserDto(
            id = requireNotNull(id) { "AppUser must have an id" },
            email = email,
            tenantId = tenantId,
        )

    private fun Invitation.toDto(): InvitationDto =
        InvitationDto(
            id = requireNotNull(id) { "id missing after save() — @GeneratedValue should populate it" },
            email = email,
            token = token,
            createdAt = requireNotNull(createdAt) { "createdAt missing after save() — @CreatedDate auditing should populate it" },
        )
}

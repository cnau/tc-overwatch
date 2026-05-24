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
        // Normalize at the boundary — DB CHECK constraints enforce lowercase storage,
        // and case-insensitive sign-in is the universal user expectation.
        val normalizedEmail = email.lowercase()
        // Advisory lock on the email serializes concurrent first sign-ins so two
        // races don't both provision separate tenants (the unique constraint would
        // catch the user INSERT but a half-built tenant row would remain). Released
        // at transaction end. hashtext collisions block unrelated emails for ms — fine.
        entityManager
            .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:email))")
            .setParameter("email", normalizedEmail)
            .singleResult
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
        // Flush so the tenant row exists when set_config runs and when the
        // app_user FK fires at INSERT — don't depend on FlushMode.AUTO.
        entityManager.flush()
        // Pre-auth path has no principal, so TenantBindingAspect can't bind for us —
        // do it ourselves so RLS WITH CHECK accepts the app_user INSERT.
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

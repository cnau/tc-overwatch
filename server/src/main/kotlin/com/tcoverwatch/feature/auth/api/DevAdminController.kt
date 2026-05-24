package com.tcoverwatch.feature.auth.api

import com.tcoverwatch.common.exception.UnauthenticatedException
import com.tcoverwatch.common.security.AuthenticatedPrincipal
import com.tcoverwatch.feature.auth.persistence.Invitation
import com.tcoverwatch.feature.auth.persistence.InvitationRepository
import com.tcoverwatch.feature.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// Dev scaffolding endpoints. Profile-gated to local — never reachable on
// unraid / prod. Used to seed invitations for local sign-in testing (closes
// #42's "minimal admin RPC" path) and to probe the TenantBindingAspect.
@RestController
@RequestMapping("/api/dev", produces = [MediaType.APPLICATION_JSON_VALUE])
@Profile("local")
class DevAdminController(
    private val invitationRepository: InvitationRepository,
    private val authService: AuthService,
) {
    @PostMapping("/invitations", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun createInvitation(
        @Valid @RequestBody request: CreateInvitationRequest,
    ): InvitationResponse {
        val saved = invitationRepository.save(Invitation(email = request.email.lowercase()))
        return InvitationResponse(
            id = requireNotNull(saved.id),
            email = saved.email,
            token = requireNotNull(saved.token) { "token populated by DB default" },
            createdAt = requireNotNull(saved.createdAt) { "createdAt populated by DB default" },
        )
    }

    // Exercises TenantBindingAspect. Does an RLS-scoped lookup for the
    // authenticated principal's own app_user row. Without the aspect setting
    // app.tenant_id, RLS hides the row → `tenantBound: false`.
    @GetMapping("/rls-probe")
    @ResponseStatus(HttpStatus.OK)
    fun rlsProbe(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal?,
    ): RlsProbeResponse {
        val userId = principal?.userId ?: throw UnauthenticatedException("Sign in required")
        val user = authService.findCurrentAppUser(userId)
        return RlsProbeResponse(
            tenantBound = user != null,
            email = user?.email,
            userId = user?.id,
        )
    }
}

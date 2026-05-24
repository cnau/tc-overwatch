package com.tcoverwatch.feature.auth.api

import com.tcoverwatch.common.security.AuthenticatedPrincipal
import com.tcoverwatch.feature.auth.service.AuthService
import com.tcoverwatch.feature.auth.service.InvitationDto
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

// Dev scaffolding: seed invitations for local sign-in testing + probe
// TenantBindingAspect end-to-end. Lives in its own file because @Profile-gated
// controllers don't share a file with unconditional ones.
@RestController
@RequestMapping("/api/dev", produces = [MediaType.APPLICATION_JSON_VALUE])
@Profile("local")
class DevAdminController(
    private val authService: AuthService,
) {
    @PostMapping("/invitations", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun createInvitation(
        @Valid @RequestBody request: CreateInvitationRequest,
    ): InvitationResponse = authService.createInvitation(request.email).toResponse()

    // Without TenantBindingAspect setting app.tenant_id, RLS hides the row → tenantBound: false.
    @GetMapping("/rls-probe")
    @ResponseStatus(HttpStatus.OK)
    fun rlsProbe(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal,
    ): RlsProbeResponse {
        val userId = requireNotNull(principal.userId) { "principal userId missing — token was issued without it" }
        val user = authService.findCurrentAppUser(userId)
        return RlsProbeResponse(
            tenantBound = user != null,
            email = user?.email,
            userId = user?.id,
        )
    }
}

internal fun InvitationDto.toResponse(): InvitationResponse =
    InvitationResponse(id = id, email = email, token = token, createdAt = createdAt)

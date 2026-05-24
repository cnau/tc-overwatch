package com.tcoverwatch.feature.auth.api

import com.tcoverwatch.common.exception.UnauthenticatedException
import com.tcoverwatch.common.security.AuthenticatedPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth", produces = [MediaType.APPLICATION_JSON_VALUE])
class AuthController {
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun me(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal?,
    ): MeResponse = principal?.toResponse() ?: throw UnauthenticatedException("Sign in required")

    // Bearer tokens are stateless — the server holds no session. The client clears
    // its stored token; this endpoint exists for symmetry / future server-side
    // revocation list. Always 204 in v0.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() = Unit
}

internal fun AuthenticatedPrincipal.toResponse(): MeResponse = MeResponse(email = email, userId = userId, tenantId = tenantId)

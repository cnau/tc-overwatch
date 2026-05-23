package com.tcoverwatch.feature.auth.api

import com.tcoverwatch.common.security.AuthCookie
import com.tcoverwatch.common.security.JwtService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// Profile-gated local-only stub: mints a JWT for any email a developer types,
// no invitation check, no Google. Wholly replaced by the real OAuth callback +
// invitation gate in PR 3 of #18. NEVER reachable on unraid / prod — the
// `@Profile("local")` annotation excludes the bean entirely from other profiles.
@RestController
@RequestMapping("/api/auth", produces = [MediaType.APPLICATION_JSON_VALUE])
@Profile("local")
class DevAuthController(
    private val jwtService: JwtService,
    private val authCookie: AuthCookie,
) {
    @PostMapping("/dev-login", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun devLogin(
        @Valid @RequestBody request: DevLoginRequest,
        response: HttpServletResponse,
    ): MeResponse {
        val token = jwtService.mint(email = request.email)
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie.set(token))
        return MeResponse(email = request.email, userId = null, tenantId = null)
    }
}

data class DevLoginRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)

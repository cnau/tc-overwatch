package com.tcoverwatch.feature.auth.api

import com.tcoverwatch.common.exception.UnauthenticatedException
import com.tcoverwatch.common.security.AuthCookie
import com.tcoverwatch.common.security.AuthenticatedPrincipal
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/auth", produces = [MediaType.APPLICATION_JSON_VALUE])
class AuthController(
    private val authCookie: AuthCookie,
) {
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal principal: AuthenticatedPrincipal?,
    ): MeResponse = principal?.toResponse() ?: throw UnauthenticatedException("Sign in required")

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> =
        ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, authCookie.clear())
            .build()
}

data class MeResponse(
    val email: String,
    val userId: UUID?,
    val tenantId: UUID?,
)

internal fun AuthenticatedPrincipal.toResponse(): MeResponse = MeResponse(email = email, userId = userId, tenantId = tenantId)

package com.tcoverwatch.feature.auth.api

import com.tcoverwatch.feature.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// Dev-only stub — routes through AuthService so the invitation gate is exercised
// in local dev, same as the real OAuth callback will when it lands.
// @Profile("local") excludes the bean from every other profile.
@RestController
@RequestMapping("/api/auth", produces = [MediaType.APPLICATION_JSON_VALUE])
@Profile("local")
class DevAuthController(
    private val authService: AuthService,
) {
    @PostMapping("/dev-login", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun devLogin(
        @Valid @RequestBody request: DevLoginRequest,
    ): LoginResponse {
        val result = authService.signIn(request.email)
        return LoginResponse(
            token = result.token,
            user =
                MeResponse(
                    email = result.email,
                    userId = result.userId,
                    tenantId = result.tenantId,
                ),
        )
    }
}

package com.tcoverwatch.feature.auth.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class OAuthFailureHandler(
    @Value("\${app.frontend-base-url}") private val frontendBaseUrl: String,
) : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        response.sendRedirect("$frontendBaseUrl/?error=OAUTH_FAILED")
    }
}

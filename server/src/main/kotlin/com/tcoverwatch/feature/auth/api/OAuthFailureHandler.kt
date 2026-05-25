package com.tcoverwatch.feature.auth.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class OAuthFailureHandler(
    @Value("\${app.frontend-base-url}") private val frontendBaseUrl: String,
) : AuthenticationFailureHandler {
    private val log = LoggerFactory.getLogger(OAuthFailureHandler::class.java)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        // The SPA only sees a generic OAUTH_FAILED — the actual cause (bad
        // client secret, JWK fetch error, clock skew, etc.) lives in this log.
        log.warn("OAuth sign-in failed", exception)
        response.sendRedirect("$frontendBaseUrl/?error=OAUTH_FAILED")
    }
}

package com.tcoverwatch.feature.auth.api

import com.tcoverwatch.common.exception.DomainException
import com.tcoverwatch.feature.auth.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class OAuthSuccessHandler(
    private val authService: AuthService,
    @Value("\${app.frontend-base-url}") private val frontendBaseUrl: String,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        try {
            val email = resolveVerifiedEmail(authentication.principal)
            val result = authService.signIn(email)
            // URL fragment doesn't leave the browser; SPA bridge clears it via history.replaceState.
            response.sendRedirect("$frontendBaseUrl/#token=${urlEncode(result.token)}")
        } catch (e: DomainException) {
            response.sendRedirect("$frontendBaseUrl/?error=${urlEncode(e.code)}")
        } catch (e: OAuthEmailResolutionException) {
            response.sendRedirect("$frontendBaseUrl/?error=${urlEncode(e.code)}")
        }
    }

    // OIDC providers (Google, Microsoft, Apple, Okta, Auth0, ...) all surface OidcUser.
    // A non-OIDC provider (e.g. GitHub OAuth2) would extend this with a per-registration resolver.
    private fun resolveVerifiedEmail(principal: Any?): String {
        if (principal !is OidcUser) throw OAuthEmailResolutionException("UNSUPPORTED_PRINCIPAL")
        val email = principal.email ?: throw OAuthEmailResolutionException("MISSING_EMAIL")
        if (principal.emailVerified != true) throw OAuthEmailResolutionException("EMAIL_NOT_VERIFIED")
        return email
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

private class OAuthEmailResolutionException(
    val code: String,
) : RuntimeException(code)

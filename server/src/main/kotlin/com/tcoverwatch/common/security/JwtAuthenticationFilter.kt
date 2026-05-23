package com.tcoverwatch.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.cookies?.firstOrNull { it.name == SESSION_COOKIE_NAME }?.value
        if (token != null) {
            jwtService.verify(token)?.let { jwt ->
                // email is the one always-present claim (see JwtService.mint). A missing
                // value means a corrupted / future-incompatible token — treat as anonymous
                // rather than NPE'ing on the non-null principal field.
                val email = jwt.getClaim<String?>("email")
                if (email != null) {
                    val principal =
                        AuthenticatedPrincipal(
                            email = email,
                            userId = jwt.getClaim<String?>("userId")?.let(UUID::fromString),
                            tenantId = jwt.getClaim<String?>("tenantId")?.let(UUID::fromString),
                        )
                    SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(principal)
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}

class JwtAuthenticationToken(
    private val authenticatedPrincipal: AuthenticatedPrincipal,
) : AbstractAuthenticationToken(emptyList()) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): AuthenticatedPrincipal = authenticatedPrincipal
}

package com.tcoverwatch.common.security

import com.tcoverwatch.common.exception.PermissionDeniedException
import com.tcoverwatch.common.exception.UnauthenticatedException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthFilter: JwtAuthenticationFilter,
        // Routes 401/403 through ApiErrorAdvice so the envelope matches the controller path.
        // @Lazy dodges the early-binding chicken-and-egg.
        @Qualifier("handlerExceptionResolver") @Lazy exceptionResolver: HandlerExceptionResolver,
    ): SecurityFilterChain {
        http
            // Bearer tokens have no ambient browser credential to forge — CSRF doesn't apply.
            .csrf(AbstractHttpConfigurer<*, *>::disable)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/api/auth/**",
                        "/api/ping",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                    ).permitAll()
                // POST /api/dev/invitations is the local-only invitation-seeding path
                // (DevAdminController, @Profile("local")). Anonymous in local; bean
                // doesn't exist in other profiles so the matcher matches nothing.
                it.requestMatchers(HttpMethod.POST, "/api/dev/invitations").permitAll()
                // Other /api/dev/* endpoints (e.g., the RLS probe) require auth.
                it.anyRequest().authenticated()
            }.exceptionHandling { eh ->
                eh.authenticationEntryPoint { req, res, _ ->
                    exceptionResolver.resolveException(req, res, null, UnauthenticatedException("Sign in required"))
                }
                eh.accessDeniedHandler { req, res, _ ->
                    exceptionResolver.resolveException(req, res, null, PermissionDeniedException("Forbidden"))
                }
            }.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}

package com.tcoverwatch.common.security

import com.tcoverwatch.common.exception.PermissionDeniedException
import com.tcoverwatch.common.exception.UnauthenticatedException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
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
        // Delegate 401/403 from the security filter chain to Spring MVC's exception
        // resolver, which routes through our @RestControllerAdvice. Same envelope
        // shape regardless of whether the failure came from a controller or the
        // security layer. @Lazy avoids the early-binding chicken-and-egg.
        @Qualifier("handlerExceptionResolver") @Lazy exceptionResolver: HandlerExceptionResolver,
    ): SecurityFilterChain {
        http
            // SPA + cookie auth on a same-parent-domain layout; SameSite=Lax + Origin
            // checks are the defense. Revisit if a third-party form-post surface ever lands.
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

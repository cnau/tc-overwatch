package com.tcoverwatch.feature.auth.api

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

// @Profile-gated so the permitAll matcher itself doesn't exist outside local —
// a future drop of @Profile on DevAdminController can't expose the path in prod.
@Configuration
@Profile("local")
class DevSecurityConfig {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun devSeedSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatchers { it.requestMatchers(HttpMethod.POST, "/api/dev/invitations") }
            .csrf(AbstractHttpConfigurer<*, *>::disable)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}

package com.tcoverwatch.feature.auth.api

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler

// Active only when a ClientRegistrationRepository exists (provided by
// GoogleClientRegistrationConfig when GOOGLE_CLIENT_ID is set).
@Configuration
@ConditionalOnBean(ClientRegistrationRepository::class)
class OAuthSecurityConfig {
    @Bean
    @Order(0)
    fun oauthSecurityFilterChain(
        http: HttpSecurity,
        successHandler: AuthenticationSuccessHandler,
        failureHandler: AuthenticationFailureHandler,
    ): SecurityFilterChain {
        http
            .securityMatchers { it.requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**") }
            .csrf(AbstractHttpConfigurer<*, *>::disable)
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .oauth2Login { o ->
                o.successHandler(successHandler)
                o.failureHandler(failureHandler)
            }
        return http.build()
    }
}

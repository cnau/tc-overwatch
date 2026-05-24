package com.tcoverwatch.feature.auth.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository

// Programmatic so an empty GOOGLE_CLIENT_ID just skips the bean — Spring Boot's
// yaml-driven autoconfig would reject empty client-id at startup and break dev boots.
@Configuration
@ConditionalOnExpression("'\${GOOGLE_CLIENT_ID:}' != ''")
class GoogleClientRegistrationConfig {
    @Bean
    fun clientRegistrationRepository(
        @Value("\${GOOGLE_CLIENT_ID}") clientId: String,
        @Value("\${GOOGLE_CLIENT_SECRET}") clientSecret: String,
    ): ClientRegistrationRepository {
        val google =
            CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "email")
                .build()
        return InMemoryClientRegistrationRepository(google)
    }
}

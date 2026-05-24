package com.tcoverwatch.feature.auth.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository

// Programmatic registration so the OAuth client only exists when GOOGLE_CLIENT_ID
// is set. Spring Boot's yaml-driven autoconfig validates a partial registration at
// startup and rejects empty client-id, which would fail local dev for anyone who
// hasn't created a Google OAuth client. Add more providers as additional @Bean
// ClientRegistration entries on this same repository builder.
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

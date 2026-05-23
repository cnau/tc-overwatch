package com.tcoverwatch.common.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.spec.SecretKeySpec

// HS256 symmetric signing. Secret comes from config (auth.jwt.secret).
// Prod sets it via env var / Secret Manager; local has a hardcoded value in
// application-local.yml. The encoder + decoder beans are also what real OAuth
// (Google) would consume, so this wiring is reusable.
@Configuration
class JwtConfig {
    @Bean
    fun jwtEncoder(
        @Value("\${auth.jwt.secret}") secret: String,
    ): JwtEncoder {
        val jwk =
            OctetSequenceKey
                .Builder(secret.toByteArray(Charsets.UTF_8))
                .algorithm(JWSAlgorithm.HS256)
                .build()
        val jwkSource: JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(jwk))
        return NimbusJwtEncoder(jwkSource)
    }

    @Bean
    fun jwtDecoder(
        @Value("\${auth.jwt.secret}") secret: String,
    ): JwtDecoder {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
    }
}

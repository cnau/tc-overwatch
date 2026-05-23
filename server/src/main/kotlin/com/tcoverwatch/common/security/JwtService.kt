package com.tcoverwatch.common.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class JwtService(
    private val encoder: JwtEncoder,
    private val decoder: JwtDecoder,
    @Value("\${auth.jwt.token-lifetime}") private val tokenLifetime: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun mint(
        email: String,
        userId: UUID? = null,
        tenantId: UUID? = null,
    ): String {
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                // `sub` always holds email — the one identity attribute present on every
                // token, whether the user record exists yet (post-invitation-acceptance)
                // or not (stub-auth path). `userId` / `tenantId` are separate claims.
                .subject(email)
                .claim("email", email)
                .apply {
                    userId?.let { claim("userId", it.toString()) }
                    tenantId?.let { claim("tenantId", it.toString()) }
                }.issuedAt(now)
                .expiresAt(now.plus(tokenLifetime))
                .build()
        val headers = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).tokenValue
    }

    fun verify(token: String): Jwt? =
        try {
            decoder.decode(token)
        } catch (e: JwtException) {
            log.debug("JWT verification failed: {}", e.message)
            null
        }
}

package com.tcoverwatch.common.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

const val SESSION_COOKIE_NAME = "tco_session"

@Component
class AuthCookie(
    @Value("\${auth.cookie.secure}") private val secure: Boolean,
    @Value("\${auth.jwt.token-lifetime}") private val tokenLifetime: Duration,
) {
    fun set(token: String): String =
        ResponseCookie
            .from(SESSION_COOKIE_NAME, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(tokenLifetime)
            .build()
            .toString()

    fun clear(): String =
        ResponseCookie
            .from(SESSION_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build()
            .toString()
}

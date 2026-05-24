package com.tcoverwatch.common.auditing

import com.tcoverwatch.common.security.AuthenticatedPrincipal
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.UUID

// Wires Spring's @CreatedBy / @LastModifiedBy / @CreatedDate / @LastModifiedDate on
// entities annotated @EntityListeners(AuditingEntityListener::class). Reads the
// authenticated principal's userId from the current SecurityContext when present —
// stub login and pre-acceptance sign-in have no userId yet, so audit fields stay
// NULL there ("system-created").
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class AuditingConfig {
    @Bean
    fun auditorAware(): AuditorAware<UUID> =
        AuditorAware {
            val principal = SecurityContextHolder.getContext().authentication?.principal
            Optional.ofNullable((principal as? AuthenticatedPrincipal)?.userId)
        }
}

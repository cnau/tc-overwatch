package com.tcoverwatch.common.auditing

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.util.Optional
import java.util.UUID

// Wires Spring's @CreatedBy / @LastModifiedBy / @CreatedDate / @LastModifiedDate on
// entities annotated @EntityListeners(AuditingEntityListener::class).
//
// v0: AuditorAware returns Optional.empty() — no authenticated principal yet, so
// created_by / updated_by stay NULL. Wire to the JWT-derived user id once PR 2 lands.
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class AuditingConfig {
    @Bean
    fun auditorAware(): AuditorAware<UUID> = AuditorAware { Optional.empty() }
}

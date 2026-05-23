package com.tcoverwatch.feature.auth.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invitation")
@EntityListeners(AuditingEntityListener::class)
class Invitation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    var id: UUID? = null,
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    var createdBy: UUID? = null,
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
    @LastModifiedBy
    @Column(name = "updated_by")
    var updatedBy: UUID? = null,
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
    @Column(name = "email", nullable = false)
    var email: String,
    @Column(name = "token", nullable = false, updatable = false)
    var token: UUID = UUID.randomUUID(),
    @Column(name = "expires_at")
    var expiresAt: Instant? = null,
    @Column(name = "accepted_at")
    var acceptedAt: Instant? = null,
    @Column(name = "tenant_id")
    var tenantId: UUID? = null,
)

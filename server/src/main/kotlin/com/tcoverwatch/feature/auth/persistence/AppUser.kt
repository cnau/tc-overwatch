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
@Table(name = "app_user")
@EntityListeners(AuditingEntityListener::class)
class AppUser(
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
    @Column(name = "tenant_id", nullable = false, updatable = false)
    var tenantId: UUID,
    @Column(name = "email", nullable = false)
    var email: String,
)

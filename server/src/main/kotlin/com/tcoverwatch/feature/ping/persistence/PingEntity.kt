package com.tcoverwatch.feature.ping.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// JPA entity for the ping_log smoke-test table.
// `internal` visibility prevents leakage outside the persistence package.
// (Hibernate requires open-class friendly access via the kotlin-jpa plugin.)
@Entity
@Table(name = "ping_log")
class PingEntity(
    @Column(name = "message", nullable = false, length = 1024)
    var message: String,
    @Column(name = "received_at", nullable = false)
    var receivedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)

package com.tcoverwatch.feature.ping.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

// Pure Spring Data JPA repository.
// Architectural convention: callers go through PingDao (which converts entities to DTOs).
// The compiler doesn't enforce this — code review does. Returning a JPA entity from a
// service or controller is a defect.
@Repository
interface PingRepository : JpaRepository<PingEntity, Long>

package com.tcoverwatch.feature.ping.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PingRepository : JpaRepository<Ping, UUID>

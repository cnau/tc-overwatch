package com.tcoverwatch.feature.ping.service

import com.tcoverwatch.feature.ping.persistence.PingDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// Service layer — business logic.
// Receives and returns ServicePingRequest/Response (no proto, no entity).
// `@Transactional` on the service method per the locked-in convention; the DAO also marks
// its mutating methods transactional (belt-and-suspenders).
@Service
class PingService(
    private val pingDao: PingDao,
) {
    @Transactional
    fun ping(request: ServicePingRequest): ServicePingResponse {
        // Business validation would go here. For Ping there's nothing meaningful to check.
        return pingDao.recordPing(request)
    }
}

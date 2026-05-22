package com.tcoverwatch.feature.ping.persistence

import com.tcoverwatch.feature.ping.service.ServicePingRequest
import com.tcoverwatch.feature.ping.service.ServicePingResponse
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

// DAO — confines Hibernate entities to the persistence layer.
//
// Accepts and returns service DTOs (ServicePingRequest / ServicePingResponse);
// converts via PingEntityMapper before/after talking to PingRepository.
// `@Transactional` here AND on the calling service per the locked-in convention.
@Repository
class PingDao(
    private val repository: PingRepository,
    private val entityMapper: PingEntityMapper,
) {
    @Transactional
    fun recordPing(request: ServicePingRequest): ServicePingResponse {
        val entity = entityMapper.toEntity(request)
        val saved = repository.save(entity)
        return entityMapper.toResponse(saved)
    }
}

package com.tcoverwatch.feature.ping.persistence

import com.tcoverwatch.feature.ping.service.PingDto
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PingDao(
    private val repository: PingRepository,
) {
    @Transactional
    fun recordPing(dto: PingDto): PingDto {
        val saved = repository.save(dto.toEntity())
        return saved.toDto()
    }
}

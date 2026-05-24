package com.tcoverwatch.feature.ping.dao

import com.tcoverwatch.feature.ping.persistence.Ping
import com.tcoverwatch.feature.ping.persistence.PingRepository
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

internal fun PingDto.toEntity(): Ping =
    Ping(
        message = message,
        receivedAt = receivedAt,
    )

internal fun Ping.toDto(): PingDto =
    PingDto(
        id = requireNotNull(id) { "Ping must have an id when mapping to PingDto" },
        message = message,
        receivedAt = receivedAt,
    )

package com.tcoverwatch.feature.ping.service

import com.tcoverwatch.feature.ping.persistence.PingDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PingService(
    private val pingDao: PingDao,
) {
    @Transactional
    fun ping(dto: PingDto): PingDto = pingDao.recordPing(dto)
}

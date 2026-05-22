package com.tcoverwatch.feature.ping.api

import com.tcoverwatch.feature.ping.service.ServicePingRequest
import com.tcoverwatch.feature.ping.service.ServicePingResponse
import com.tcoverwatch.v1.PingRequest
import com.tcoverwatch.v1.PingResponse
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import java.time.Instant

// MapStruct boundary mapper: proto wire types <-> service DTOs.
// Generated impl is wired into Spring as a bean.
@Mapper(componentModel = "spring", imports = [Instant::class])
interface PingProtoMapper {

    @Mapping(target = "receivedAt", expression = "java(java.time.Instant.now())")
    fun toServiceRequest(request: PingRequest): ServicePingRequest

    @Mapping(source = "receivedAt", target = "serverReceivedAt", qualifiedByName = ["toIsoString"])
    fun toProtoResponse(response: ServicePingResponse): PingResponse

    @org.mapstruct.Named("toIsoString")
    fun instantToIso(instant: Instant): String = instant.toString()
}

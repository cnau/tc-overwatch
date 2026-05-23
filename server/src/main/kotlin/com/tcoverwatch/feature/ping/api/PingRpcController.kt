package com.tcoverwatch.feature.ping.api

import com.tcoverwatch.feature.ping.service.PingService
import com.tcoverwatch.v1.PingRequest
import com.tcoverwatch.v1.PingResponse
import com.tcoverwatch.v1.PingServiceGrpc
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.grpc.server.service.GrpcService

// gRPC controller — wire-format entry point for the PingService RPC.
//
// Responsibilities per layering rules:
//   - Convert proto request -> service DTO (via the toServiceRequest extension).
//   - Apply *shape* validation only (no DB queries here).
//   - Delegate business logic to PingService.
//   - Convert service DTO -> proto response (via the toProtoResponse extension).
//   - Never returns or accepts JPA entities — those are confined to the persistence layer.
@GrpcService
class PingRpcController(
    private val pingService: PingService,
) : PingServiceGrpc.PingServiceImplBase() {
    override fun ping(
        request: PingRequest,
        responseObserver: StreamObserver<PingResponse>,
    ) {
        try {
            validateShape(request)
            val serviceResponse = pingService.ping(request.toServiceRequest())
            responseObserver.onNext(serviceResponse.toProtoResponse())
            responseObserver.onCompleted()
        } catch (e: IllegalArgumentException) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException(),
            )
        }
    }

    private fun validateShape(request: PingRequest) {
        require(request.message.isNotBlank()) { "message must not be blank" }
        require(request.message.length <= MAX_MESSAGE_LENGTH) {
            "message must not exceed $MAX_MESSAGE_LENGTH characters"
        }
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 1024
    }
}

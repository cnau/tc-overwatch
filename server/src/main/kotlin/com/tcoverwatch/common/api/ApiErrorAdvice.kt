package com.tcoverwatch.common.api

import com.tcoverwatch.common.exception.ConflictException
import com.tcoverwatch.common.exception.FailedPreconditionException
import com.tcoverwatch.common.exception.NotFoundException
import com.tcoverwatch.common.exception.PermissionDeniedException
import com.tcoverwatch.common.exception.UnauthenticatedException
import com.tcoverwatch.common.exception.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiErrorAdvice {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException) = respond(HttpStatus.NOT_FOUND, e.code, e.message)

    @ExceptionHandler(PermissionDeniedException::class)
    fun handlePermissionDenied(e: PermissionDeniedException) = respond(HttpStatus.FORBIDDEN, e.code, e.message)

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(e: UnauthenticatedException) = respond(HttpStatus.UNAUTHORIZED, e.code, e.message)

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(e: ConflictException) = respond(HttpStatus.CONFLICT, e.code, e.message)

    @ExceptionHandler(FailedPreconditionException::class)
    fun handleFailedPrecondition(e: FailedPreconditionException) = respond(HttpStatus.UNPROCESSABLE_ENTITY, e.code, e.message)

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(e: ValidationException): ResponseEntity<ApiErrorResponse> {
        val details =
            e.fieldErrors?.let { errors ->
                mapOf(
                    "fieldErrors" to
                        errors.map { fe ->
                            mapOf(
                                "field" to fe.field,
                                "message" to fe.message,
                                "rejectedValue" to fe.rejectedValue,
                            )
                        },
                )
            }
        return respond(HttpStatus.BAD_REQUEST, e.code, e.message, details)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val fieldErrors =
            e.bindingResult.fieldErrors.map { fe ->
                mapOf(
                    "field" to fe.field,
                    "message" to (fe.defaultMessage ?: "invalid"),
                    "rejectedValue" to fe.rejectedValue,
                )
            }
        val summary =
            fieldErrors
                .firstOrNull()
                ?.let { "${it["field"]}: ${it["message"]}" }
                ?: "Request validation failed"
        return respond(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_FAILED",
            message = summary,
            details = mapOf("fieldErrors" to fieldErrors),
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException) =
        respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body could not be parsed")

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(e: HttpRequestMethodNotSupportedException) =
        respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", e.message)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(e: HttpMediaTypeNotSupportedException) =
        respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", e.message)

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleNotAcceptable(e: HttpMediaTypeNotAcceptableException) = respond(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", e.message)

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(e: MissingServletRequestParameterException) = respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", e.message)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException) =
        respond(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", "Parameter '${e.name}' has invalid type")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException) = respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found")

    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception): ResponseEntity<ApiErrorResponse> {
        log.error("Unhandled exception", e)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong")
    }

    private fun respond(
        status: HttpStatus,
        code: String,
        message: String?,
        details: Map<String, Any?>? = null,
    ): ResponseEntity<ApiErrorResponse> {
        if (status.is4xxClientError) {
            log.info("Client error {} {} {}", status.value(), code, message)
        }
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                code = code,
                message = message ?: status.reasonPhrase,
                details = details,
            ),
        )
    }
}

package com.tcoverwatch.common.exception

sealed class DomainException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class NotFoundException(
    message: String,
    cause: Throwable? = null,
) : DomainException(code = "NOT_FOUND", message = message, cause = cause)

class PermissionDeniedException(
    message: String,
    cause: Throwable? = null,
) : DomainException(code = "PERMISSION_DENIED", message = message, cause = cause)

class UnauthenticatedException(
    message: String,
    cause: Throwable? = null,
) : DomainException(code = "UNAUTHENTICATED", message = message, cause = cause)

class ConflictException(
    message: String,
    cause: Throwable? = null,
) : DomainException(code = "CONFLICT", message = message, cause = cause)

// 422 Unprocessable Entity for business-state failures (closed transaction, expired invite).
// 412 Precondition Failed is reserved for HTTP-level preconditions (If-Match etc.).
class FailedPreconditionException(
    message: String,
    cause: Throwable? = null,
) : DomainException(code = "FAILED_PRECONDITION", message = message, cause = cause)

// Distinct from FAILED_PRECONDITION so the frontend can branch on code — invitation-only
// rejection has a specific UI (different from generic business-state failures).
class InvitationRequiredException(
    message: String,
    cause: Throwable? = null,
) : DomainException(code = "INVITATION_REQUIRED", message = message, cause = cause)

// Service-layer shape-validation failure. The controller's `@Valid` path uses Jakarta's
// MethodArgumentNotValidException — mapped separately by the advice into the same envelope.
class ValidationException(
    message: String,
    val fieldErrors: List<FieldError>? = null,
    cause: Throwable? = null,
) : DomainException(code = "VALIDATION_FAILED", message = message, cause = cause)

data class FieldError(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null,
)

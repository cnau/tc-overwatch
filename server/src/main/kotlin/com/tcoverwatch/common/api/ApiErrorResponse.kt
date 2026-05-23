package com.tcoverwatch.common.api

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, Any?>? = null,
)

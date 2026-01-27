package com.example.retrofitdemo.network

/**
 * ApiResult: A sealed class that represents the result of an API call.
 * 
 * This pattern is useful for:
 * - Encapsulating success/error states in a type-safe way
 * - Avoiding exceptions for expected error cases
 * - Making error handling explicit and exhaustive with when expressions
 * 
 * Usage example:
 * ```
 * when (result) {
 *     is ApiResult.Success -> handleData(result.data)
 *     is ApiResult.Error -> handleError(result.exception, result.errorBody)
 * }
 * ```
 */
sealed class ApiResult<out T> {
    /**
     * Represents a successful API response.
     * @param data The parsed response body
     */
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * Represents a failed API response.
     * @param exception The exception that caused the failure (network, parsing, etc.)
     * @param errorBody The raw error response body, if available
     */
    data class Error(
        val exception: Throwable,
        val errorBody: String? = null
    ) : ApiResult<Nothing>()
}

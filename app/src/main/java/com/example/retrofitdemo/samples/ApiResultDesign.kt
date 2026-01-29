package com.example.retrofitdemo.samples

import com.example.retrofitdemo.network.ApiResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * ApiResultDesign: Demonstrates the ApiResult sealed class design pattern.
 * 
 * Learning nodes:
 * - L3-6: Type-safe error handling with sealed classes
 * - L4-1: Advanced error handling patterns
 * 
 * This sample demonstrates:
 * 1. ApiResult sealed class for encapsulating success/error states
 * 2. Type-safe error handling with when expressions
 * 3. Avoiding exceptions for expected error cases
 * 4. Making error handling explicit and exhaustive
 * 
 * Source reading notes:
 * - Sealed classes provide type-safe discriminated unions
 * - Forces explicit handling of all cases
 * - Better than throwing exceptions for expected errors
 * - Integrates well with Kotlin's when expression
 * - See also: ApiResultCallAdapter for automatic wrapping
 * 
 * Design principles:
 * - Success<T> contains the parsed data
 * - Error contains the exception and optional error body
 * - No null handling needed - sealed class enforces exhaustive checks
 */
object ApiResultDesign {
    
    /**
     * Simple data model for testing.
     */
    data class User(
        val id: Int,
        val username: String,
        val email: String
    )
    
    /**
     * Service interface returning raw results (not wrapped in ApiResult yet).
     * This demonstrates manual ApiResult wrapping.
     */
    interface UserService {
        @GET("users/{id}")
        fun getUser(@Path("id") id: Int): Call<User>
    }
    
    /**
     * Creates a Retrofit instance for UserService.
     * 
     * @param baseUrl The base URL for the API
     * @return Configured Retrofit instance
     */
    fun createRetrofit(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Creates the UserService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return UserService implementation
     */
    fun createService(retrofit: Retrofit): UserService {
        return retrofit.create(UserService::class.java)
    }
    
    /**
     * Example: Manual wrapping of API call result in ApiResult.
     * 
     * This demonstrates how to convert a standard Retrofit call
     * into an ApiResult without using the CallAdapter.
     * 
     * @param call The Call to execute
     * @return ApiResult wrapping the response
     */
    fun <T> wrapInApiResult(call: Call<T>): ApiResult<T> {
        return try {
            val response = call.execute()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(
                        exception = NullPointerException("Response body is null"),
                        errorBody = null
                    )
                }
            } else {
                ApiResult.Error(
                    exception = Exception("HTTP ${response.code()}: ${response.message()}"),
                    errorBody = response.errorBody()?.string()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(
                exception = e,
                errorBody = null
            )
        }
    }
    
    /**
     * Example: Handling ApiResult with when expression.
     * 
     * This demonstrates the recommended way to handle ApiResult.
     * The when expression is exhaustive, ensuring all cases are handled.
     * 
     * @param result The ApiResult to handle
     * @return A user-friendly message
     */
    fun handleResult(result: ApiResult<User>): String {
        return when (result) {
            is ApiResult.Success -> {
                val user = result.data
                "Success: User ${user.username} (${user.email})"
            }
            is ApiResult.Error -> {
                val message = result.exception.message ?: "Unknown error"
                val errorBody = result.errorBody?.let { " - Error body: $it" } ?: ""
                "Error: $message$errorBody"
            }
        }
    }
    
    /**
     * Example: Extracting data from ApiResult with fold.
     * 
     * This provides a functional approach to handling ApiResult.
     * 
     * @param result The ApiResult to process
     * @param onSuccess Callback for success case
     * @param onError Callback for error case
     * @return The result of the callback
     */
    fun <T, R> fold(
        result: ApiResult<T>,
        onSuccess: (T) -> R,
        onError: (Throwable, String?) -> R
    ): R {
        return when (result) {
            is ApiResult.Success -> onSuccess(result.data)
            is ApiResult.Error -> onError(result.exception, result.errorBody)
        }
    }
    
    /**
     * Example: Chaining transformations on ApiResult.
     * 
     * This demonstrates how to transform the data inside ApiResult
     * without unwrapping and re-wrapping.
     * 
     * @param result The ApiResult to transform
     * @param transform The transformation function
     * @return Transformed ApiResult
     */
    fun <T, R> map(result: ApiResult<T>, transform: (T) -> R): ApiResult<R> {
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(transform(result.data))
            is ApiResult.Error -> ApiResult.Error(result.exception, result.errorBody)
        }
    }
    
    /**
     * Example: Getting data or default value.
     * 
     * @param result The ApiResult to extract from
     * @param default The default value if error
     * @return The data or default
     */
    fun <T> getOrDefault(result: ApiResult<T>, default: T): T {
        return when (result) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> default
        }
    }
    
    /**
     * Example: Getting data or null.
     * 
     * @param result The ApiResult to extract from
     * @return The data or null
     */
    fun <T> getOrNull(result: ApiResult<T>): T? {
        return when (result) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> null
        }
    }
}

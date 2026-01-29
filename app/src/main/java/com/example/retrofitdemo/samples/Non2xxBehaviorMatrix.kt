package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

/**
 * Non2xxBehaviorMatrix: Matrix of HTTP status codes and their handling behavior.
 * 
 * Learning nodes:
 * - L1-4: Understanding HTTP status codes
 * - L4-7: Advanced error handling strategies
 * 
 * This sample demonstrates:
 * 1. HTTP 2xx (success) vs non-2xx (error) behavior
 * 2. How different return types handle non-2xx responses
 * 3. Client errors (4xx) vs server errors (5xx)
 * 4. Special status codes (304, 401, 403, 429, etc.)
 * 
 * Source reading notes:
 * - 2xx: Success (200 OK, 201 Created, 204 No Content)
 * - 3xx: Redirection (handled by OkHttp automatically)
 * - 4xx: Client error (400 Bad Request, 404 Not Found, 401 Unauthorized)
 * - 5xx: Server error (500 Internal Server Error, 503 Service Unavailable)
 * - Call<T>: Allows inspection of any status code
 * - suspend T: Throws HttpException for non-2xx
 * - suspend Response<T>: Allows inspection of any status code
 */
object Non2xxBehaviorMatrix {
    
    /**
     * Simple data model for testing.
     */
    data class Item(
        val id: Int,
        val name: String
    )
    
    /**
     * Service interface for testing different status codes.
     */
    interface StatusCodeService {
        @GET("status/200")
        fun get200(): Call<Item>
        
        @GET("status/204")
        fun get204(): Call<Item>
        
        @GET("status/400")
        fun get400(): Call<Item>
        
        @GET("status/401")
        fun get401(): Call<Item>
        
        @GET("status/403")
        fun get403(): Call<Item>
        
        @GET("status/404")
        fun get404(): Call<Item>
        
        @GET("status/429")
        fun get429(): Call<Item>
        
        @GET("status/500")
        fun get500(): Call<Item>
        
        @GET("status/503")
        fun get503(): Call<Item>
        
        @GET("status/200")
        suspend fun get200Suspend(): Response<Item>
        
        @GET("status/404")
        suspend fun get404Suspend(): Response<Item>
    }
    
    /**
     * Creates a Retrofit instance for StatusCodeService.
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
     * Creates the StatusCodeService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return StatusCodeService implementation
     */
    fun createService(retrofit: Retrofit): StatusCodeService {
        return retrofit.create(StatusCodeService::class.java)
    }
    
    /**
     * Represents the behavior of a status code.
     */
    data class StatusCodeBehavior(
        val code: Int,
        val category: String,
        val description: String,
        val isSuccessful: Boolean,
        val hasBody: Boolean,
        val throwsInSuspendDirect: Boolean
    )
    
    /**
     * Gets behavior matrix for common HTTP status codes.
     */
    fun getBehaviorMatrix(): List<StatusCodeBehavior> {
        return listOf(
            // 2xx Success
            StatusCodeBehavior(
                code = 200,
                category = "Success",
                description = "OK - Request succeeded",
                isSuccessful = true,
                hasBody = true,
                throwsInSuspendDirect = false
            ),
            StatusCodeBehavior(
                code = 201,
                category = "Success",
                description = "Created - Resource created",
                isSuccessful = true,
                hasBody = true,
                throwsInSuspendDirect = false
            ),
            StatusCodeBehavior(
                code = 204,
                category = "Success",
                description = "No Content - Success but no body",
                isSuccessful = true,
                hasBody = false,
                throwsInSuspendDirect = false
            ),
            // 4xx Client Errors
            StatusCodeBehavior(
                code = 400,
                category = "Client Error",
                description = "Bad Request - Invalid request format",
                isSuccessful = false,
                hasBody = true,
                throwsInSuspendDirect = true
            ),
            StatusCodeBehavior(
                code = 401,
                category = "Client Error",
                description = "Unauthorized - Authentication required",
                isSuccessful = false,
                hasBody = true,
                throwsInSuspendDirect = true
            ),
            StatusCodeBehavior(
                code = 403,
                category = "Client Error",
                description = "Forbidden - No permission",
                isSuccessful = false,
                hasBody = true,
                throwsInSuspendDirect = true
            ),
            StatusCodeBehavior(
                code = 404,
                category = "Client Error",
                description = "Not Found - Resource doesn't exist",
                isSuccessful = false,
                hasBody = true,
                throwsInSuspendDirect = true
            ),
            StatusCodeBehavior(
                code = 429,
                category = "Client Error",
                description = "Too Many Requests - Rate limit exceeded",
                isSuccessful = false,
                hasBody = true,
                throwsInSuspendDirect = true
            ),
            // 5xx Server Errors
            StatusCodeBehavior(
                code = 500,
                category = "Server Error",
                description = "Internal Server Error",
                isSuccessful = false,
                hasBody = true,
                throwsInSuspendDirect = true
            ),
            StatusCodeBehavior(
                code = 503,
                category = "Server Error",
                description = "Service Unavailable - Server down/overloaded",
                isSuccessful = false,
                hasBody = true,
                throwsInSuspendDirect = true
            )
        )
    }
    
    /**
     * Categorizes a status code.
     */
    fun categorizeStatusCode(code: Int): String {
        return when (code) {
            in 200..299 -> "Success (2xx)"
            in 300..399 -> "Redirection (3xx)"
            in 400..499 -> "Client Error (4xx)"
            in 500..599 -> "Server Error (5xx)"
            else -> "Unknown"
        }
    }
    
    /**
     * Checks if a status code is successful.
     * Retrofit considers 2xx as successful.
     */
    fun isSuccessful(code: Int): Boolean {
        return code in 200..299
    }
    
    /**
     * Checks if a status code typically has a response body.
     * 204 No Content and 205 Reset Content don't have bodies.
     */
    fun hasBody(code: Int): Boolean {
        return code !in listOf(204, 205)
    }
}

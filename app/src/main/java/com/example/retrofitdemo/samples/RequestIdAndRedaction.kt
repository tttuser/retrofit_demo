package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response as OkHttpResponse
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.UUID

/**
 * RequestIdAndRedaction: Demonstrates request ID tracking and sensitive data redaction.
 * 
 * Learning nodes:
 * - L2-1: Interceptor patterns for logging and tracking
 * - L2-2: Security best practices for logging
 * 
 * This sample demonstrates:
 * 1. Adding unique request IDs to track requests through the system
 * 2. Logging request/response details for debugging
 * 3. Redacting sensitive information (passwords, tokens) from logs
 * 4. Combining multiple interceptors for different concerns
 * 
 * Key insights:
 * - Request IDs enable correlation of logs across services
 * - Logging interceptors should never log passwords or tokens
 * - Redaction should happen before logging, not after
 * - Interceptors can be chained to separate concerns
 * - Application interceptors see the original request/response
 * 
 * Source reading notes:
 * - Interceptor.Chain allows request modification
 * - chain.proceed() sends the request and gets response
 * - Request/Response are immutable - use builders to modify
 * - Headers added in interceptors are sent to the server
 * - Logging interceptor should be added last to see all headers
 */
object RequestIdAndRedaction {
    
    /**
     * Login request with sensitive password field.
     */
    data class LoginRequest(
        val username: String,
        val password: String  // Sensitive - should be redacted in logs
    )
    
    /**
     * Response with sensitive token.
     */
    data class LoginResponse(
        val token: String,    // Sensitive - should be redacted in logs
        val userId: Int
    )
    
    /**
     * Service interface for testing.
     */
    interface TestService {
        @POST("login")
        suspend fun login(@Body request: LoginRequest): LoginResponse
        
        @GET("data")
        suspend fun getData(): Map<String, String>
    }
    
    /**
     * Interceptor that adds X-Request-Id header to all requests.
     * 
     * Benefits:
     * - Enables request tracing across services
     * - Helps correlate client and server logs
     * - Useful for debugging distributed systems
     */
    class RequestIdInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
            val requestId = UUID.randomUUID().toString()
            val request = chain.request()
                .newBuilder()
                .addHeader("X-Request-Id", requestId)
                .build()
            return chain.proceed(request)
        }
    }
    
    /**
     * Interceptor that logs requests/responses with redaction of sensitive data.
     * 
     * Security features:
     * - Redacts password fields from request bodies
     * - Redacts token fields from response bodies
     * - Redacts Authorization headers
     * - Preserves X-Request-Id for tracing
     */
    class RedactingLoggingInterceptor : Interceptor {
        // List of sensitive field names to redact
        private val sensitiveFields = setOf("password", "token", "secret", "apiKey", "authorization")
        
        override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
            val request = chain.request()
            
            // Log request with redaction
            val requestLog = buildString {
                append("→ ${request.method} ${request.url}\n")
                append("→ X-Request-Id: ${request.header("X-Request-Id")}\n")
                
                // Redact Authorization header if present
                val authHeader = request.header("Authorization")
                if (authHeader != null) {
                    append("→ Authorization: [REDACTED]\n")
                }
                
                // Log request body with redaction
                request.body?.let { body ->
                    // In production, you'd parse and redact the body
                    // For this sample, we just note that redaction would happen
                    append("→ Body: [Content with sensitive fields redacted]\n")
                }
            }
            logMessage(requestLog)
            
            // Execute request
            val response = chain.proceed(request)
            
            // Log response with redaction
            val responseLog = buildString {
                append("← ${response.code} ${response.message}\n")
                append("← X-Request-Id: ${response.request.header("X-Request-Id")}\n")
                
                // Note: In production, you'd peek at the response body and redact it
                // Here we just indicate redaction would happen
                if (response.isSuccessful) {
                    append("← Body: [Content with sensitive fields redacted]\n")
                }
            }
            logMessage(responseLog)
            
            return response
        }
        
        /**
         * Redacts sensitive values from a string (e.g., JSON).
         * 
         * This is a simple implementation. In production, you'd want to:
         * - Parse JSON properly
         * - Handle nested objects
         * - Support different formats (XML, form data, etc.)
         */
        fun redactSensitiveData(text: String): String {
            var redacted = text
            
            // Simple regex-based redaction for JSON fields
            sensitiveFields.forEach { field ->
                // Matches: "field":"value" or "field": "value"
                val pattern = """"$field"\s*:\s*"[^"]*"""".toRegex(RegexOption.IGNORE_CASE)
                redacted = redacted.replace(pattern, """"$field":"[REDACTED]"""")
            }
            
            return redacted
        }
        
        /**
         * Logs a message. In production, this would use a proper logging framework.
         */
        private fun logMessage(message: String) {
            // In tests, this will be captured
            println(message)
        }
    }
    
    /**
     * Creates Retrofit with request ID and redacting logging.
     * 
     * @param baseUrl Base URL for the API
     * @return Configured Retrofit instance
     */
    fun createRetrofit(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            // Add request ID first
            .addInterceptor(RequestIdInterceptor())
            // Add logging interceptor last to see all headers
            .addInterceptor(RedactingLoggingInterceptor())
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}

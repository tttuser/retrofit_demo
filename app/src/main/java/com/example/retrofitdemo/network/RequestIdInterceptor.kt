package com.example.retrofitdemo.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * RequestIdInterceptor: Adds a unique X-Request-Id header to every outgoing request.
 * 
 * Why this is useful:
 * - Enables request tracing across distributed systems
 * - Helps correlate logs between client and server
 * - Useful for debugging and monitoring
 * 
 * Implementation notes:
 * - Generates a new UUID for each request
 * - Adds the header before the request is sent (chain.proceed)
 * - The header will be visible in server logs and can be returned in error responses
 */
class RequestIdInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestId = UUID.randomUUID().toString()
        val request = chain.request()
            .newBuilder()
            .addHeader("X-Request-Id", requestId)
            .build()
        return chain.proceed(request)
    }
}

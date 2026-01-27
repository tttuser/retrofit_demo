package com.example.retrofitdemo.network

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset

/**
 * LoggingInterceptor: Logs HTTP request and response details with simple redaction.
 * 
 * Features:
 * - Logs request method, URL, and headers
 * - Logs response code, headers, and body
 * - Redacts sensitive data (passwords, tokens, authorization headers)
 * - Uses UTF-8 charset for reading response bodies
 * 
 * Implementation notes:
 * - Uses response.peekBody() to avoid consuming the response stream
 * - Applies simple regex-based redaction for common sensitive fields
 * - Logs to println for demo purposes (in production, use proper logging framework)
 * 
 * Source reading note:
 * - peekBody() creates a copy of the response body without consuming it,
 *   allowing Retrofit to still parse the original response
 */
class LoggingInterceptor : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Log request
        println("→ ${request.method} ${request.url}")
        request.headers.forEach { (name, value) ->
            val redactedValue = if (name.equals("Authorization", ignoreCase = true)) {
                "[REDACTED]"
            } else {
                value
            }
            println("  $name: $redactedValue")
        }
        
        // Log request body if present
        request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
            val bodyString = buffer.readString(charset)
            println("  Body: ${redactSensitiveData(bodyString)}")
        }
        
        val response = chain.proceed(request)
        
        // Log response
        println("← ${response.code} ${request.url}")
        response.headers.forEach { (name, value) ->
            println("  $name: $value")
        }
        
        // Peek at response body without consuming it
        val responseBodyString = response.peekBody(1024 * 1024).string()
        println("  Body: ${redactSensitiveData(responseBodyString)}")
        
        return response
    }
    
    /**
     * Simple redaction of sensitive data in request/response bodies.
     * Redacts common sensitive fields like password, token, secret, etc.
     */
    private fun redactSensitiveData(input: String): String {
        return input
            .replace(Regex("\"password\"\\s*:\\s*\"[^\"]*\""), "\"password\":\"[REDACTED]\"")
            .replace(Regex("\"token\"\\s*:\\s*\"[^\"]*\""), "\"token\":\"[REDACTED]\"")
            .replace(Regex("\"secret\"\\s*:\\s*\"[^\"]*\""), "\"secret\":\"[REDACTED]\"")
            .replace(Regex("\"apiKey\"\\s*:\\s*\"[^\"]*\""), "\"apiKey\":\"[REDACTED]\"")
            .replace(Regex("password=[^&]*"), "password=[REDACTED]")
    }
}

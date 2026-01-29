package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

/**
 * FirstCallSlowTest: Demonstrates first call overhead and warmup behavior.
 * 
 * Learning nodes:
 * - L3-3: Understanding first-call latency in Retrofit/OkHttp
 * - L3-4: Warmup strategies for performance-critical applications
 * 
 * This sample demonstrates:
 * 1. First HTTP call has additional overhead (DNS, connection, SSL handshake)
 * 2. Subsequent calls benefit from connection pooling
 * 3. Measuring performance differences between first and subsequent calls
 * 4. Warmup strategies to hide first-call latency
 * 
 * Key insights:
 * - First call includes DNS lookup (unless cached by OS)
 * - TLS handshake adds significant overhead on first connection
 * - Connection pool reuses existing connections for subsequent calls
 * - OkHttp's connection pool keeps connections alive for reuse
 * - Warmup calls can pre-establish connections
 * 
 * Source reading notes:
 * - ConnectionPool manages persistent HTTP connections
 * - RealConnection handles actual socket connections
 * - First call triggers DNS lookup via DNS resolver
 * - SSL handshake involves certificate verification
 */
object FirstCallSlowTest {
    
    /**
     * Simple data class for API response.
     */
    data class PingResponse(val timestamp: Long, val server: String)
    
    /**
     * Test service interface for performance measurement.
     */
    interface PingService {
        @GET("ping")
        suspend fun ping(): PingResponse
    }
    
    /**
     * Performance measurement result.
     */
    data class TimingResult(
        val durationMs: Long,
        val timestamp: Long
    )
    
    /**
     * Creates a Retrofit instance for timing tests.
     * 
     * @param baseUrl Base URL for the API
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
     * Measures execution time of a suspend function.
     * 
     * @param block The suspend function to measure
     * @return TimingResult with duration in milliseconds
     */
    suspend fun measureTime(block: suspend () -> Unit): TimingResult {
        val startTime = System.currentTimeMillis()
        block()
        val endTime = System.currentTimeMillis()
        return TimingResult(
            durationMs = endTime - startTime,
            timestamp = startTime
        )
    }
    
    /**
     * Executes multiple calls and measures each one.
     * 
     * This demonstrates the performance difference between:
     * - First call (cold start)
     * - Subsequent calls (warm, with connection reuse)
     * 
     * @param service The service to call
     * @param count Number of calls to make
     * @return List of timing results for each call
     */
    suspend fun measureMultipleCalls(
        service: PingService,
        count: Int = 3
    ): List<TimingResult> {
        val results = mutableListOf<TimingResult>()
        
        repeat(count) {
            val result = measureTime {
                service.ping()
            }
            results.add(result)
        }
        
        return results
    }
    
    /**
     * Analyzes timing results to identify first-call overhead.
     * 
     * @param results List of timing results
     * @return Map with timing statistics
     */
    fun analyzeTimings(results: List<TimingResult>): Map<String, Any> {
        if (results.isEmpty()) {
            return emptyMap()
        }
        
        val firstCallMs = results.first().durationMs
        val subsequentCalls = results.drop(1)
        val avgSubsequentMs = if (subsequentCalls.isNotEmpty()) {
            subsequentCalls.map { it.durationMs }.average()
        } else {
            0.0
        }
        
        return mapOf(
            "firstCallMs" to firstCallMs,
            "avgSubsequentMs" to avgSubsequentMs,
            "overhead" to (firstCallMs - avgSubsequentMs),
            "overheadPercent" to if (avgSubsequentMs > 0) {
                ((firstCallMs - avgSubsequentMs) / avgSubsequentMs * 100)
            } else {
                0.0
            },
            "callCount" to results.size
        )
    }
    
    /**
     * Performs a warmup call to pre-establish connections.
     * 
     * This can be used to hide first-call latency by warming up
     * the connection pool before actual work begins.
     * 
     * @param service The service to warm up
     * @return Duration of warmup call in milliseconds
     */
    suspend fun warmup(service: PingService): Long {
        val result = measureTime {
            service.ping()
        }
        return result.durationMs
    }
}

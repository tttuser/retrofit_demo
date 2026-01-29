package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * TimeoutTuning: Demonstrates configuring OkHttp timeout settings.
 * 
 * Learning nodes:
 * - L2-5: Timeout configuration for different scenarios
 * - L4-3: Handling slow networks and servers
 * 
 * This sample demonstrates:
 * 1. Three types of timeouts: connect, read, and write
 * 2. When to use each timeout type
 * 3. How timeouts affect request behavior
 * 4. Appropriate timeout values for different scenarios
 * 
 * Key insights:
 * - Connect timeout: Time to establish TCP connection (default: 10s)
 * - Read timeout: Time between receiving data chunks (default: 10s)
 * - Write timeout: Time between sending data chunks (default: 10s)
 * - Total request time can exceed read timeout if data keeps flowing
 * - Setting timeout to 0 means no timeout (infinite wait)
 * 
 * Source reading notes:
 * - Timeouts are enforced by OkHttp's AsyncTimeout mechanism
 * - Each timeout type protects against different failure modes
 * - Connect timeout protects against slow DNS and TCP handshake
 * - Read timeout protects against stalled downloads
 * - Write timeout protects against stalled uploads
 * - Timeouts should balance between reliability and user experience
 */
object TimeoutTuning {
    
    /**
     * Simple data class for API response.
     */
    data class Data(val value: String)
    
    /**
     * Test service interface.
     */
    interface TimeoutService {
        @GET("data")
        suspend fun getData(): Data
    }
    
    /**
     * Creates Retrofit with default timeouts.
     * 
     * Default values (OkHttp defaults):
     * - Connect timeout: 10 seconds
     * - Read timeout: 10 seconds
     * - Write timeout: 10 seconds
     * 
     * These are reasonable for most use cases.
     * 
     * @param baseUrl Base URL for the API
     * @return Configured Retrofit instance
     */
    fun createWithDefaultTimeouts(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
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
    
    /**
     * Creates Retrofit with short timeouts.
     * 
     * Use case: Fast-fail scenarios where quick response is critical
     * - Mobile app on unreliable network
     * - User-facing operations that need quick feedback
     * - Health checks and ping endpoints
     * 
     * @param baseUrl Base URL for the API
     * @param timeoutSeconds Timeout in seconds (applied to all three types)
     * @return Configured Retrofit instance
     */
    fun createWithShortTimeouts(baseUrl: String, timeoutSeconds: Long = 5): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
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
    
    /**
     * Creates Retrofit with long timeouts.
     * 
     * Use case: Slow operations that legitimately take time
     * - Large file uploads/downloads
     * - Long-running server operations (reports, exports)
     * - Batch processing endpoints
     * 
     * @param baseUrl Base URL for the API
     * @param timeoutSeconds Timeout in seconds (applied to all three types)
     * @return Configured Retrofit instance
     */
    fun createWithLongTimeouts(baseUrl: String, timeoutSeconds: Long = 60): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
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
    
    /**
     * Creates Retrofit with different timeouts for each type.
     * 
     * Use case: Fine-tuned configuration for specific scenarios
     * - Short connect timeout (network issues are common)
     * - Long read timeout (server processing takes time)
     * - Medium write timeout (uploads are moderate)
     * 
     * @param baseUrl Base URL for the API
     * @param connectSeconds Connect timeout in seconds
     * @param readSeconds Read timeout in seconds
     * @param writeSeconds Write timeout in seconds
     * @return Configured Retrofit instance
     */
    fun createWithCustomTimeouts(
        baseUrl: String,
        connectSeconds: Long = 10,
        readSeconds: Long = 30,
        writeSeconds: Long = 20
    ): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(connectSeconds, TimeUnit.SECONDS)
            .readTimeout(readSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeSeconds, TimeUnit.SECONDS)
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
    
    /**
     * Gets the configured timeouts from an OkHttpClient.
     * 
     * @param client The OkHttpClient to inspect
     * @return Map of timeout types to their values in seconds
     */
    fun getTimeouts(client: OkHttpClient): Map<String, Long> {
        return mapOf(
            "connect" to client.connectTimeoutMillis / 1000,
            "read" to client.readTimeoutMillis / 1000,
            "write" to client.writeTimeoutMillis / 1000
        )
    }
}

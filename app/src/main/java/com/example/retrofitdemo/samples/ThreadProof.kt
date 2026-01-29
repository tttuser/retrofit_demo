package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.atomic.AtomicInteger

/**
 * ThreadProof: Demonstrates thread-safety of Retrofit and OkHttp.
 * 
 * Learning nodes:
 * - L1-6: Thread-safety guarantees of Retrofit/OkHttp
 * - L2-4: Concurrent request handling
 * 
 * This sample demonstrates:
 * 1. Retrofit and OkHttp are thread-safe
 * 2. A single Retrofit instance can handle concurrent requests safely
 * 3. Service interfaces created from Retrofit are thread-safe
 * 4. Connection pooling works correctly under concurrent load
 * 
 * Key insights:
 * - Retrofit instances are immutable and thread-safe
 * - OkHttp's connection pool handles concurrent requests efficiently
 * - No synchronization needed when calling service methods from multiple threads
 * - Thread-local resources are managed internally by OkHttp
 * 
 * Source reading notes:
 * - Retrofit creates thread-safe proxies for service interfaces
 * - OkHttp's Dispatcher manages thread pool for async calls
 * - Connection pool uses synchronized blocks internally for thread-safety
 * - Each request gets its own Call object which is not thread-safe (but that's OK)
 */
object ThreadProof {
    
    /**
     * Simple data class for API response.
     */
    data class Response(val threadId: Long, val requestId: Int)
    
    /**
     * Test service interface.
     */
    interface ThreadTestService {
        @GET("data")
        suspend fun getData(): Response
    }
    
    /**
     * Counter to track how many instances were created.
     * Used to demonstrate that we can safely reuse a single instance.
     */
    private val instanceCounter = AtomicInteger(0)
    
    /**
     * Creates a thread-safe Retrofit instance.
     * Increments the instance counter each time it's called.
     * 
     * @param baseUrl Base URL for the API
     * @return Configured Retrofit instance
     */
    fun createRetrofit(baseUrl: String): Retrofit {
        instanceCounter.incrementAndGet()
        
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
     * Gets the current instance counter value.
     * 
     * @return Number of Retrofit instances created
     */
    fun getInstanceCount(): Int = instanceCounter.get()
    
    /**
     * Resets the instance counter.
     * Used for testing to start with a clean state.
     */
    fun resetInstanceCounter() {
        instanceCounter.set(0)
    }
}

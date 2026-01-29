package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

/**
 * CreateTwiceBenchmark: Demonstrates the cost of creating Retrofit instances.
 * 
 * Learning nodes:
 * - L1-1: Retrofit instance creation best practices
 * - L2-3: Performance implications of multiple instances
 * 
 * This sample demonstrates:
 * 1. The overhead of creating multiple Retrofit instances
 * 2. Why you should reuse Retrofit instances (singleton pattern)
 * 3. Performance comparison between inefficient and efficient patterns
 * 
 * Key insight:
 * - Creating a Retrofit instance is expensive (OkHttpClient creation, thread pools, etc.)
 * - Reuse instances whenever possible via singleton pattern
 * - Creating instances per-request is a common anti-pattern
 * 
 * Source reading notes:
 * - Retrofit.Builder() creates new thread pools and connection pools
 * - OkHttpClient.Builder() also creates new pools and dispatchers
 * - Reusing instances shares these resources efficiently
 */
object CreateTwiceBenchmark {
    
    /**
     * Simple data class for API response.
     */
    data class Item(val id: Int, val name: String)
    
    /**
     * Test service interface.
     */
    interface TestService {
        @GET("items")
        suspend fun getItems(): List<Item>
    }
    
    /**
     * INEFFICIENT: Creates a new Retrofit instance for each call.
     * This is an anti-pattern that wastes resources.
     * 
     * Problems:
     * - Creates new thread pools for each instance
     * - Creates new connection pools for each instance
     * - More memory allocation and GC pressure
     * - Slower overall performance
     */
    fun createRetrofitEveryTime(baseUrl: String): Retrofit {
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
     * EFFICIENT: Reuses a single Retrofit instance.
     * This is the recommended pattern.
     * 
     * Benefits:
     * - Shares thread pools across all requests
     * - Shares connection pools (connection reuse)
     * - Less memory allocation
     * - Better performance
     */
    class RetrofitSingleton(baseUrl: String) {
        private val retrofit: Retrofit
        
        init {
            val okHttpClient = OkHttpClient.Builder().build()
            
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
        
        fun getRetrofit(): Retrofit = retrofit
    }
    
    /**
     * Measures the time to create multiple Retrofit instances.
     * 
     * @param count Number of instances to create
     * @param baseUrl Base URL for the instances
     * @return Time in milliseconds
     */
    fun measureCreateTime(count: Int, baseUrl: String): Long {
        val startTime = System.currentTimeMillis()
        repeat(count) {
            createRetrofitEveryTime(baseUrl)
        }
        val endTime = System.currentTimeMillis()
        return endTime - startTime
    }
    
    /**
     * Measures the time to reuse a single Retrofit instance.
     * 
     * @param count Number of times to access the instance
     * @param baseUrl Base URL for the instance
     * @return Time in milliseconds
     */
    fun measureReuseTime(count: Int, baseUrl: String): Long {
        val singleton = RetrofitSingleton(baseUrl)
        val startTime = System.currentTimeMillis()
        repeat(count) {
            singleton.getRetrofit()
        }
        val endTime = System.currentTimeMillis()
        return endTime - startTime
    }
}

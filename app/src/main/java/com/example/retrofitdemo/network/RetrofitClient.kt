package com.example.retrofitdemo.network

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * RetrofitClient: Factory object for creating Retrofit API service instances.
 * 
 * This demonstrates a complete Retrofit + OkHttp + Moshi setup with:
 * - Custom interceptors (RequestIdInterceptor, LoggingInterceptor)
 * - Disk cache for HTTP responses
 * - Configurable timeouts
 * - Custom CallAdapter (ApiResultCallAdapterFactory)
 * - Moshi JSON converter with Kotlin support
 * 
 * Source reading notes:
 * - OkHttpClient handles the actual HTTP communication
 * - Retrofit provides the API abstraction layer
 * - Moshi handles JSON serialization/deserialization
 * - Interceptors are executed in order: application interceptors -> network interceptors
 * - Cache is stored on disk and respects HTTP cache headers
 */
object RetrofitClient {
    
    // Base URL for the API (using JSONPlaceholder as example API)
    private const val BASE_URL = "https://jsonplaceholder.typicode.com/"
    
    // Timeout configurations (in seconds)
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    
    // Cache size: 10 MB
    private const val CACHE_SIZE = 10L * 1024 * 1024
    
    /**
     * Creates an OkHttpClient with custom configuration.
     * 
     * Configuration includes:
     * - RequestIdInterceptor: Adds X-Request-Id header to all requests
     * - LoggingInterceptor: Logs request/response details with redaction
     * - Disk cache: Caches responses to reduce network calls
     * - Timeouts: Prevents requests from hanging indefinitely
     * 
     * @param context Android context for cache directory
     * @return Configured OkHttpClient instance
     */
    private fun createOkHttpClient(context: Context): OkHttpClient {
        // Create cache directory in app's cache folder
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, CACHE_SIZE)
        
        return OkHttpClient.Builder()
            // Add custom interceptors
            // Note: Interceptors are executed in the order they are added
            .addInterceptor(RequestIdInterceptor())
            .addInterceptor(LoggingInterceptor())
            
            // Configure cache
            .cache(cache)
            
            // Configure timeouts
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            
            .build()
    }
    
    /**
     * Creates a Moshi instance for JSON parsing.
     * 
     * Moshi configuration:
     * - KotlinJsonAdapterFactory: Enables Kotlin-specific features like default parameter values
     * - Add this last so custom adapters take precedence
     * 
     * @return Configured Moshi instance
     */
    private fun createMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    /**
     * Creates a Retrofit instance with full configuration.
     * 
     * Retrofit configuration:
     * - Base URL: All API endpoints are relative to this URL
     * - OkHttpClient: Handles HTTP communication
     * - MoshiConverterFactory: Converts JSON to/from Kotlin objects
     * - ApiResultCallAdapterFactory: Wraps responses in ApiResult
     * 
     * @param context Android context for cache directory
     * @return Configured Retrofit instance
     */
    private fun createRetrofit(context: Context): Retrofit {
        val okHttpClient = createOkHttpClient(context)
        val moshi = createMoshi()
        
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(ApiResultCallAdapterFactory())
            .build()
    }
    
    /**
     * Creates an instance of the ApiService.
     * 
     * Usage:
     * ```
     * val apiService = RetrofitClient.createApiService(context)
     * val user = apiService.getUser(userId = 1)
     * ```
     * 
     * @param context Android context for cache directory
     * @return ApiService implementation
     */
    fun createApiService(context: Context): ApiService {
        return createRetrofit(context).create(ApiService::class.java)
    }
}

package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.io.File

/**
 * OkHttpCacheLab: Demonstrates HTTP caching with OkHttp.
 * 
 * Learning nodes:
 * - L2-6: HTTP caching mechanisms
 * - L4-4: Cache configuration and behavior
 * 
 * This sample demonstrates:
 * 1. Configuring disk cache for HTTP responses
 * 2. How HTTP cache headers control caching behavior
 * 3. Cache hits vs cache misses
 * 4. Cache size limits and eviction
 * 5. Inspecting cache statistics
 * 
 * Key insights:
 * - OkHttp respects standard HTTP cache headers (Cache-Control, ETag, etc.)
 * - Cache-Control: max-age=X means cache for X seconds
 * - Cache-Control: no-cache means always validate with server
 * - Cache reduces network calls and improves performance
 * - Cache requires disk space - set appropriate size limits
 * 
 * HTTP Caching Headers:
 * - Cache-Control: Directives for caching (max-age, no-cache, no-store)
 * - ETag: Identifier for resource version (enables conditional requests)
 * - Last-Modified: When resource was last modified
 * - Expires: Absolute expiration time (older alternative to max-age)
 * 
 * Source reading notes:
 * - Cache stores responses on disk in a structured format
 * - Cache key is based on request URL and method
 * - LRU (Least Recently Used) eviction when cache is full
 * - Cache is thread-safe and shared across all requests
 * - Response.cacheResponse indicates if response came from cache
 * - Response.networkResponse indicates if request hit network
 */
object OkHttpCacheLab {
    
    /**
     * Simple data class for API response.
     */
    data class CacheableData(val value: String, val timestamp: Long)
    
    /**
     * Service interface for cache testing.
     */
    interface CacheTestService {
        @GET("cacheable")
        suspend fun getCacheableData(): Response<CacheableData>
        
        @GET("no-cache")
        suspend fun getNoCacheData(): Response<CacheableData>
    }
    
    /**
     * Creates Retrofit with HTTP cache enabled.
     * 
     * @param cacheDir Directory to store cached responses
     * @param cacheSizeBytes Maximum cache size in bytes
     * @return Configured Retrofit instance
     */
    fun createWithCache(cacheDir: File, cacheSizeBytes: Long = 10 * 1024 * 1024): Retrofit {
        val cache = Cache(cacheDir, cacheSizeBytes)
        
        val okHttpClient = OkHttpClient.Builder()
            .cache(cache)
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl("https://example.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Creates Retrofit without cache.
     * All requests will hit the network.
     * 
     * @return Configured Retrofit instance
     */
    fun createWithoutCache(): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            // No cache configured
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl("https://example.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Checks if a response came from cache or network.
     * 
     * @param response The response to inspect
     * @return Map with cache hit/miss information
     */
    fun analyzeCacheUsage(response: Response<*>): Map<String, Any> {
        val raw = response.raw()
        
        return mapOf(
            "fromCache" to (raw.cacheResponse != null && raw.networkResponse == null),
            "fromNetwork" to (raw.networkResponse != null && raw.cacheResponse == null),
            "conditionalHit" to (raw.cacheResponse != null && raw.networkResponse != null),
            "cacheResponseCode" to (raw.cacheResponse?.code ?: -1),
            "networkResponseCode" to (raw.networkResponse?.code ?: -1)
        )
    }
    
    /**
     * Gets cache statistics.
     * 
     * @param client The OkHttpClient with cache
     * @return Map with cache statistics
     */
    fun getCacheStats(client: OkHttpClient): Map<String, Long> {
        val cache = client.cache ?: return emptyMap()
        
        return mapOf(
            "requestCount" to cache.requestCount(),
            "networkCount" to cache.networkCount(),
            "hitCount" to cache.hitCount(),
            "currentSizeBytes" to cache.size(),
            "maxSizeBytes" to cache.maxSize()
        )
    }
    
    /**
     * Clears the cache.
     * 
     * @param client The OkHttpClient with cache
     */
    suspend fun clearCache(client: OkHttpClient) {
        client.cache?.evictAll()
    }
}

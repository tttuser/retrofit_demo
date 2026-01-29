package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * CustomHostAndNoAuth: Demonstrates dynamic host configuration and auth-less endpoints.
 * 
 * Learning nodes:
 * - L2-3: Dynamic URL configuration
 * - L2-4: Authentication bypass patterns
 * 
 * This sample demonstrates:
 * 1. Using @Url annotation for full URL override
 * 2. Using @Header to override host dynamically
 * 3. Interceptor-based host switching
 * 4. Bypassing authentication for specific endpoints
 * 
 * Source reading notes:
 * - @Url allows complete URL override, ignoring base URL
 * - Custom headers can signal special handling (e.g., X-Use-Custom-Host)
 * - Interceptors can modify requests based on headers or URL patterns
 * - Useful for multi-tenant systems, CDN resources, or public endpoints
 */
object CustomHostAndNoAuth {
    
    /**
     * Simple data model for testing.
     */
    data class Resource(
        val id: Int,
        val name: String,
        val url: String
    )
    
    /**
     * Service interface with custom host support.
     */
    interface DynamicHostService {
        /**
         * Standard endpoint using base URL.
         */
        @GET("resources/standard")
        suspend fun getStandardResource(): Resource
        
        /**
         * Endpoint with full URL override using @Url.
         * Ignores base URL completely.
         */
        @GET
        suspend fun getResourceFromUrl(@Url fullUrl: String): Resource
        
        /**
         * Endpoint with custom host specified via header.
         * Interceptor will rewrite the URL to use custom host.
         */
        @GET("resources/custom")
        suspend fun getResourceFromCustomHost(
            @Header("X-Use-Custom-Host") customHost: String
        ): Resource
        
        /**
         * Public endpoint that bypasses authentication.
         * Uses header to signal no auth needed.
         */
        @GET("resources/public")
        suspend fun getPublicResource(
            @Header("X-No-Auth") noAuth: String = "true"
        ): Resource
    }
    
    /**
     * Interceptor that supports dynamic host switching.
     * 
     * Reads X-Use-Custom-Host header and rewrites the URL.
     */
    class DynamicHostInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val customHost = originalRequest.header("X-Use-Custom-Host")
            
            return if (customHost != null) {
                // Rewrite URL to use custom host
                val newUrl = HttpUrl.Builder()
                    .scheme(originalRequest.url.scheme)
                    .host(customHost)
                    .encodedPath(originalRequest.url.encodedPath)
                    .query(originalRequest.url.query)
                    .build()
                
                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .removeHeader("X-Use-Custom-Host") // Remove marker header
                    .build()
                
                chain.proceed(newRequest)
            } else {
                chain.proceed(originalRequest)
            }
        }
    }
    
    /**
     * Interceptor that adds authentication to requests.
     * Skips authentication if X-No-Auth header is present.
     */
    class ConditionalAuthInterceptor(private val authToken: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val noAuth = originalRequest.header("X-No-Auth")
            
            return if (noAuth != null) {
                // Remove the marker header and proceed without auth
                val newRequest = originalRequest.newBuilder()
                    .removeHeader("X-No-Auth")
                    .build()
                chain.proceed(newRequest)
            } else {
                // Add authorization header
                val newRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $authToken")
                    .build()
                chain.proceed(newRequest)
            }
        }
    }
    
    /**
     * Creates a Retrofit instance with dynamic host support.
     * 
     * @param baseUrl The default base URL
     * @param authToken Optional authentication token
     * @return Configured Retrofit instance
     */
    fun createRetrofit(baseUrl: String, authToken: String? = null): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(DynamicHostInterceptor())
        
        // Add auth interceptor if token provided
        if (authToken != null) {
            clientBuilder.addInterceptor(ConditionalAuthInterceptor(authToken))
        }
        
        val okHttpClient = clientBuilder.build()
        
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
     * Creates the DynamicHostService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return DynamicHostService implementation
     */
    fun createService(retrofit: Retrofit): DynamicHostService {
        return retrofit.create(DynamicHostService::class.java)
    }
    
    /**
     * Use cases for dynamic host configuration.
     */
    enum class UseCase(val description: String) {
        MULTI_TENANT("Different hosts for different tenants"),
        CDN_RESOURCES("Fetch resources from CDN instead of API server"),
        FAILOVER("Switch to backup host on error"),
        REGIONAL("Use region-specific hosts based on user location"),
        PUBLIC_ENDPOINTS("Bypass auth for public endpoints like health checks")
    }
    
    /**
     * Gets all use cases for dynamic host configuration.
     */
    fun getUseCases(): List<UseCase> {
        return UseCase.values().toList()
    }
}

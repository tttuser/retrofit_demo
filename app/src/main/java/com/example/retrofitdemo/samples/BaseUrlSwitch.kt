package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response as OkHttpResponse
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

/**
 * BaseUrlSwitch: Demonstrates switching base URLs dynamically.
 * 
 * Learning nodes:
 * - L2-7: Dynamic configuration and environment switching
 * - L4-5: Multi-environment support patterns
 * 
 * This sample demonstrates:
 * 1. Switching between different environments (dev, staging, production)
 * 2. Using interceptors to dynamically change base URL
 * 3. Creating multiple Retrofit instances for different environments
 * 4. Runtime environment selection
 * 
 * Key insights:
 * - Base URL is set at Retrofit build time, but can be overridden per-request
 * - Interceptors can modify request URLs before sending
 * - Multiple Retrofit instances can target different servers
 * - Environment switching is common for dev/staging/prod deployments
 * - Consider using BuildConfig or environment variables for configuration
 * 
 * Source reading notes:
 * - Retrofit's baseUrl() is used as fallback for relative paths
 * - Interceptors can replace the entire URL if needed
 * - HttpUrl is OkHttp's immutable URL representation
 * - newBuilder() creates a mutable copy for modifications
 * - Use absolute URLs in @GET to bypass base URL
 */
object BaseUrlSwitch {
    
    /**
     * Environment configuration.
     */
    enum class Environment(val baseUrl: String) {
        DEVELOPMENT("https://dev-api.example.com/"),
        STAGING("https://staging-api.example.com/"),
        PRODUCTION("https://api.example.com/")
    }
    
    /**
     * Simple data class for API response.
     */
    data class ApiInfo(
        val environment: String,
        val version: String
    )
    
    /**
     * Service interface for testing.
     */
    interface ApiService {
        @GET("info")
        suspend fun getInfo(): ApiInfo
    }
    
    /**
     * Interceptor that dynamically switches base URL.
     * 
     * This allows changing the target server without rebuilding Retrofit.
     */
    class BaseUrlInterceptor(private var baseUrl: HttpUrl) : Interceptor {
        
        /**
         * Updates the base URL for subsequent requests.
         */
        fun setBaseUrl(newBaseUrl: HttpUrl) {
            baseUrl = newBaseUrl
        }
        
        /**
         * Gets the current base URL.
         */
        fun getBaseUrl(): HttpUrl = baseUrl
        
        override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url
            
            // Replace the scheme, host, and port with the base URL
            val newUrl = originalUrl.newBuilder()
                .scheme(baseUrl.scheme)
                .host(baseUrl.host)
                .port(baseUrl.port)
                .build()
            
            val newRequest = originalRequest.newBuilder()
                .url(newUrl)
                .build()
            
            return chain.proceed(newRequest)
        }
    }
    
    /**
     * Creates Retrofit with a specific environment.
     * 
     * This is the simplest approach - create separate instances for each environment.
     * 
     * @param environment The target environment
     * @return Configured Retrofit instance
     */
    fun createForEnvironment(environment: Environment): Retrofit {
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(environment.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Creates Retrofit with dynamic base URL switching.
     * 
     * This approach uses an interceptor to allow runtime URL changes.
     * 
     * @param initialEnvironment The initial environment
     * @return Pair of Retrofit instance and BaseUrlInterceptor for switching
     */
    fun createWithDynamicBaseUrl(initialEnvironment: Environment): Pair<Retrofit, BaseUrlInterceptor> {
        val interceptor = BaseUrlInterceptor(initialEnvironment.baseUrl.toHttpUrl())
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        // Use a placeholder base URL since interceptor will override it
        val retrofit = Retrofit.Builder()
            .baseUrl(initialEnvironment.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        return retrofit to interceptor
    }
    
    /**
     * Factory for creating services for different environments.
     * 
     * This demonstrates a clean pattern for managing multiple environments.
     */
    class EnvironmentManager(private var currentEnvironment: Environment) {
        private val retrofitInstances = mutableMapOf<Environment, Retrofit>()
        
        /**
         * Gets a Retrofit instance for the current environment.
         * Instances are cached for reuse.
         */
        fun getCurrentRetrofit(): Retrofit {
            return retrofitInstances.getOrPut(currentEnvironment) {
                createForEnvironment(currentEnvironment)
            }
        }
        
        /**
         * Switches to a different environment.
         */
        fun switchEnvironment(newEnvironment: Environment) {
            currentEnvironment = newEnvironment
        }
        
        /**
         * Gets the current environment.
         */
        fun getCurrentEnvironment(): Environment = currentEnvironment
        
        /**
         * Creates a service for the current environment.
         */
        inline fun <reified T> createService(): T {
            return getCurrentRetrofit().create(T::class.java)
        }
    }
}

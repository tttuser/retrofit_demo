package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.UUID
import okhttp3.Interceptor
import okhttp3.Response as OkHttpResponse

/**
 * HelloRetrofitSample: Minimal Retrofit demonstration.
 * 
 * Learning nodes:
 * - L1-1: Basic Retrofit creation and service instantiation
 * - L1-7: Simple GET request with path and query parameters
 * 
 * This sample demonstrates:
 * 1. Creating a minimal Retrofit instance
 * 2. Defining a simple service interface
 * 3. Making GET requests with path and query parameters
 * 4. Adding request IDs for tracing
 * 
 * Source reading notes:
 * - Retrofit converts interface methods to HTTP calls
 * - @Path substitutes values in URL path
 * - @Query adds query string parameters
 * - suspend functions enable coroutine support
 */
object HelloRetrofitSample {
    
    /**
     * Simple data class for the Hello API response.
     */
    data class HelloResponse(
        val message: String,
        val id: Int
    )
    
    /**
     * Minimal service interface with GET endpoint.
     */
    interface HelloService {
        /**
         * Simple GET request with path and query parameters.
         * GET /hello/{id}?name={name}
         * 
         * @param id Path parameter
         * @param name Query parameter
         * @return HelloResponse containing greeting message
         */
        @GET("hello/{id}")
        suspend fun getHello(
            @Path("id") id: Int,
            @Query("name") name: String? = null
        ): HelloResponse
    }
    
    /**
     * Simple interceptor that adds X-Request-Id header to all requests.
     * Replicates the behavior of RequestIdInterceptor for this sample.
     */
    class SimpleRequestIdInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
            val requestId = UUID.randomUUID().toString()
            val request = chain.request()
                .newBuilder()
                .addHeader("X-Request-Id", requestId)
                .build()
            return chain.proceed(request)
        }
    }
    
    /**
     * Creates a minimal Retrofit instance.
     * 
     * This demonstrates the bare minimum needed:
     * 1. Base URL
     * 2. OkHttpClient (with optional interceptors)
     * 3. JSON converter (Moshi)
     * 
     * @param baseUrl The base URL for the API
     * @return Configured Retrofit instance
     */
    fun createRetrofit(baseUrl: String): Retrofit {
        // Create OkHttpClient with request ID interceptor
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(SimpleRequestIdInterceptor())
            .build()
        
        // Create Moshi for JSON parsing
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        // Create Retrofit instance
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Creates the HelloService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return HelloService implementation
     */
    fun createService(retrofit: Retrofit): HelloService {
        return retrofit.create(HelloService::class.java)
    }
}

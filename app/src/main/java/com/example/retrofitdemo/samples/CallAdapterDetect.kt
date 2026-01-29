package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.lang.reflect.Type

/**
 * CallAdapterDetect: Demonstrates how Retrofit selects CallAdapters for different return types.
 * 
 * Learning nodes:
 * - L3-1: Understanding CallAdapter mechanism
 * - L3-2: How Retrofit matches return types to CallAdapters
 * 
 * This sample demonstrates:
 * 1. Default CallAdapter for Call<T>
 * 2. Built-in CallAdapter for suspend functions
 * 3. How to detect which CallAdapter is selected
 * 4. Custom CallAdapter registration and selection
 * 
 * Source reading notes:
 * - Retrofit tries each registered CallAdapter.Factory in order
 * - The first factory that returns non-null handles the return type
 * - Built-in adapters: DefaultCallAdapterFactory, CompletableFutureCallAdapterFactory
 * - Kotlin coroutines support is provided by BuiltInConverters
 */
object CallAdapterDetect {
    
    /**
     * Simple data model for testing.
     */
    data class Item(
        val id: Int,
        val name: String
    )
    
    /**
     * Service interface with different return types.
     * Each return type will use a different CallAdapter.
     */
    interface DetectionService {
        /**
         * Returns Call<T> - uses DefaultCallAdapterFactory
         * This is the default behavior when no suspend modifier is used.
         */
        @GET("items/call")
        fun getItemAsCall(): Call<Item>
        
        /**
         * Returns suspend T - uses built-in coroutines CallAdapter
         * Retrofit provides this adapter automatically when Kotlin coroutines are available.
         */
        @GET("items/suspend")
        suspend fun getItemSuspend(): Item
        
        /**
         * Returns suspend Response<T> - uses built-in coroutines CallAdapter
         * Same adapter as suspend T, but wraps result in Response.
         */
        @GET("items/suspend-response")
        suspend fun getItemSuspendResponse(): Response<Item>
    }
    
    /**
     * Custom CallAdapter.Factory that logs when it's invoked.
     * This allows us to detect when Retrofit queries this factory.
     */
    class LoggingCallAdapterFactory : CallAdapter.Factory() {
        val queriedTypes = mutableListOf<Type>()
        
        override fun get(
            returnType: Type,
            annotations: Array<out Annotation>,
            retrofit: Retrofit
        ): CallAdapter<*, *>? {
            // Log that this factory was queried
            queriedTypes.add(returnType)
            
            // Return null - we don't actually handle any types
            // This lets Retrofit continue to the next factory
            return null
        }
        
        /**
         * Gets a human-readable description of queried types.
         */
        fun getQueriedTypesDescription(): List<String> {
            return queriedTypes.map { type ->
                type.typeName
            }
        }
    }
    
    /**
     * Creates a Retrofit instance with logging CallAdapter factory.
     * The logging factory is registered first, so it sees all type queries.
     * 
     * @param baseUrl The base URL for the API
     * @param loggingFactory The logging factory to track queries
     * @return Configured Retrofit instance
     */
    fun createRetrofit(baseUrl: String, loggingFactory: LoggingCallAdapterFactory): Retrofit {
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addCallAdapterFactory(loggingFactory) // Add logging factory first
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Creates the DetectionService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return DetectionService implementation
     */
    fun createService(retrofit: Retrofit): DetectionService {
        return retrofit.create(DetectionService::class.java)
    }
    
    /**
     * Helper to check if a type uses the default Call adapter.
     * Call<T> types use Retrofit's DefaultCallAdapterFactory.
     */
    fun usesDefaultCallAdapter(returnType: Type): Boolean {
        return returnType.typeName.startsWith("retrofit2.Call<")
    }
    
    /**
     * Helper to check if a type uses the coroutines adapter.
     * Suspend functions are handled by Retrofit's built-in coroutines support.
     */
    fun usesCoroutinesAdapter(returnType: Type): Boolean {
        // Note: Suspend functions don't show up as special types in CallAdapter.Factory
        // because Retrofit transforms them before they reach the adapter
        return returnType.typeName.contains("kotlin.coroutines.Continuation")
    }
}

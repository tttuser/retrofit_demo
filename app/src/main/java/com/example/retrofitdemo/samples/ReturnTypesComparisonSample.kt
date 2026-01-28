package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * ReturnTypesComparisonSample: Demonstrates different Retrofit return types.
 * 
 * Learning nodes:
 * - L1-3: Different return type behaviors
 * - L3-7: Error handling differences between return types
 * 
 * This sample demonstrates:
 * 1. Call<T>: Lazy execution, cancellable, synchronous or asynchronous
 * 2. suspend T: Direct result, throws on error, coroutine-based
 * 3. suspend Response<T>: Full HTTP response, checks isSuccessful, coroutine-based
 * 
 * Source reading notes:
 * - Call<T>: Must be executed explicitly, good for cancellation
 * - suspend T: Simplest, but throws exceptions on HTTP errors
 * - suspend Response<T>: Best for handling both success and error cases
 * - For non-2xx responses: Call and Response allow inspection, T throws
 */
object ReturnTypesComparisonSample {
    
    /**
     * Simple data model for testing.
     */
    data class Item(
        val id: Int,
        val name: String
    )
    
    /**
     * Service interface demonstrating different return types.
     */
    interface ComparisonService {
        /**
         * Returns Call<T> - lazy execution pattern.
         * Must call execute() or enqueue() to run.
         * Can be cancelled with call.cancel().
         * 
         * @param id Item ID
         * @return Call that will return Item when executed
         */
        @GET("items/{id}")
        fun getItemAsCall(@Path("id") id: Int): Call<Item>
        
        /**
         * Returns suspend T - direct result pattern.
         * Throws exception on non-2xx responses.
         * Simplest to use for happy path.
         * 
         * @param id Item ID
         * @return Item directly
         * @throws retrofit2.HttpException on non-2xx responses
         */
        @GET("items/{id}")
        suspend fun getItemDirect(@Path("id") id: Int): Item
        
        /**
         * Returns suspend Response<T> - full response pattern.
         * Includes HTTP metadata (status code, headers).
         * Check isSuccessful to handle success/error cases.
         * 
         * @param id Item ID
         * @return Response wrapping Item
         */
        @GET("items/{id}")
        suspend fun getItemAsResponse(@Path("id") id: Int): Response<Item>
    }
    
    /**
     * Creates a Retrofit instance for ComparisonService.
     * 
     * @param baseUrl The base URL for the API
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
     * Creates the ComparisonService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return ComparisonService implementation
     */
    fun createService(retrofit: Retrofit): ComparisonService {
        return retrofit.create(ComparisonService::class.java)
    }
}

package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * JsonBodyPostSample: Demonstrates POST requests with JSON body using Moshi.
 * 
 * Learning node:
 * - L1-2: POST requests with JSON body serialization
 * 
 * This sample demonstrates:
 * 1. Creating DTOs (Data Transfer Objects) for request/response
 * 2. Using @Body annotation for JSON request body
 * 3. Moshi automatic serialization/deserialization
 * 4. POST request handling
 * 
 * Source reading notes:
 * - @Body annotation tells Retrofit to serialize the object as request body
 * - Moshi handles the JSON serialization automatically
 * - Content-Type: application/json is set automatically
 * - Response is automatically deserialized from JSON
 */
object JsonBodyPostSample {
    
    /**
     * Request DTO for creating a new item.
     */
    data class CreateItemRequest(
        val name: String,
        val description: String,
        val price: Double,
        val tags: List<String>
    )
    
    /**
     * Response DTO for created item.
     */
    data class CreateItemResponse(
        val id: Long,
        val name: String,
        val description: String,
        val price: Double,
        val tags: List<String>,
        val createdAt: String
    )
    
    /**
     * Service interface demonstrating JSON POST.
     */
    interface ItemService {
        /**
         * Creates a new item by posting JSON body.
         * POST /items
         * 
         * @param request The item data to create
         * @return CreateItemResponse with created item details including ID
         */
        @POST("items")
        suspend fun createItem(@Body request: CreateItemRequest): CreateItemResponse
    }
    
    /**
     * Creates a Retrofit instance for ItemService.
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
     * Creates the ItemService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return ItemService implementation
     */
    fun createService(retrofit: Retrofit): ItemService {
        return retrofit.create(ItemService::class.java)
    }
}

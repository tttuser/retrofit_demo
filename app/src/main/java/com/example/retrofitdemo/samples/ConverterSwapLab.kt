package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * ConverterSwapLab: Demonstrates swapping and layering converters.
 * 
 * Learning nodes:
 * - L4-2: Understanding converter factory precedence
 * - L4-3: Multiple converters and content-type negotiation
 * - L4-4: Custom converter implementation
 * 
 * This sample demonstrates:
 * 1. How Retrofit selects converters based on order
 * 2. Multiple converter factories working together
 * 3. Converter factory precedence rules
 * 4. Different converters for different response types
 * 5. String vs JSON conversion
 * 
 * Key insights:
 * - Converters are tried in the order they're added
 * - First converter that can handle the type wins
 * - ScalarsConverterFactory handles primitive types and String
 * - MoshiConverterFactory handles JSON objects
 * - Content-Type header influences converter selection
 * - Return type determines which converter is used
 * 
 * Source reading notes:
 * - Retrofit.nextResponseBodyConverter() iterates converters
 * - Converter.Factory.responseBodyConverter() returns null if can't handle
 * - BuiltInConverters handles basic types (ResponseBody, Void, etc.)
 * - Order matters: add more specific converters first
 */
object ConverterSwapLab {
    
    /**
     * Data class for JSON responses.
     */
    data class JsonData(val value: String, val count: Int)
    
    /**
     * Service interface demonstrating different return types.
     */
    interface ConverterTestService {
        /**
         * Returns plain text string.
         * Requires ScalarsConverterFactory.
         */
        @GET("text")
        suspend fun getText(): String
        
        /**
         * Returns JSON object.
         * Uses MoshiConverterFactory.
         */
        @GET("json")
        suspend fun getJson(): JsonData
        
        /**
         * Posts plain text and returns JSON.
         * Request uses ScalarsConverter, response uses MoshiConverter.
         */
        @POST("echo")
        suspend fun echoText(@Body text: String): JsonData
    }
    
    /**
     * Creates Retrofit with only Moshi converter.
     * This can handle JSON but not plain text.
     * 
     * @param baseUrl Base URL for the API
     * @return Retrofit instance with Moshi converter only
     */
    fun createRetrofitJsonOnly(baseUrl: String): Retrofit {
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
     * Creates Retrofit with Scalars converter first, then Moshi.
     * This can handle both plain text and JSON.
     * ScalarsConverter handles String, Moshi handles objects.
     * 
     * @param baseUrl Base URL for the API
     * @return Retrofit instance with both converters
     */
    fun createRetrofitWithBothConverters(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Creates Retrofit with reversed converter order (Moshi first, Scalars second).
     * Order matters: Moshi is tried first for all types.
     * 
     * @param baseUrl Base URL for the API
     * @return Retrofit instance with reversed converter order
     */
    fun createRetrofitReversedOrder(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }
    
    /**
     * Gets information about configured converters.
     * 
     * Note: Retrofit doesn't expose converter list directly,
     * but we can infer from behavior.
     * 
     * @param retrofit The Retrofit instance
     * @return Map describing converter configuration
     */
    fun getConverterInfo(retrofit: Retrofit): Map<String, Any> {
        // We can't directly access converter list from Retrofit API,
        // but we can document what we configured
        return mapOf(
            "baseUrl" to retrofit.baseUrl().toString(),
            "note" to "Converter order is determined by addConverterFactory() call order"
        )
    }
    
    /**
     * Demonstrates converter selection based on return type.
     * 
     * This is a utility to help understand which converter
     * would be selected for different return types.
     * 
     * @param returnType Description of the return type
     * @param hasScalars Whether ScalarsConverterFactory is present
     * @param hasMoshi Whether MoshiConverterFactory is present
     * @param scalarsFirst Whether Scalars is added before Moshi
     * @return Description of which converter would be used
     */
    fun predictConverter(
        returnType: String,
        hasScalars: Boolean,
        hasMoshi: Boolean,
        scalarsFirst: Boolean
    ): String {
        return when (returnType) {
            "String" -> {
                when {
                    hasScalars -> "ScalarsConverterFactory"
                    hasMoshi -> "MoshiConverterFactory (will try to parse as JSON String)"
                    else -> "No suitable converter"
                }
            }
            "JsonObject" -> {
                when {
                    hasMoshi -> "MoshiConverterFactory"
                    else -> "No suitable converter"
                }
            }
            else -> "Depends on converter capabilities"
        }
    }
}

package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

/**
 * RequestShapeSuite: Comprehensive demonstration of HTTP request shapes.
 * 
 * Learning nodes:
 * - L3-5: Understanding different request body types
 * - L3-6: Query parameters, headers, and path parameters
 * - L3-7: Request annotation combinations
 * 
 * This sample demonstrates:
 * 1. Various ways to shape HTTP requests with Retrofit annotations
 * 2. Path parameters (@Path) for URL path segments
 * 3. Query parameters (@Query, @QueryMap) for URL query strings
 * 4. Headers (@Header, @HeaderMap) for HTTP headers
 * 5. Request bodies (@Body) for POST/PUT payloads
 * 6. Form-encoded requests (@Field, @FieldMap)
 * 7. URL-encoded vs JSON bodies
 * 
 * Key insights:
 * - Annotations determine how arguments are serialized
 * - Multiple parameter types can be combined in one method
 * - @QueryMap and @HeaderMap accept Map<String, String>
 * - @Body uses converter factory (e.g., Moshi for JSON)
 * - @Field requires @FormUrlEncoded on the method
 * 
 * Source reading notes:
 * - ParameterHandler processes each annotation type
 * - RequestBuilder assembles the final OkHttp Request
 * - Converter handles serialization based on Content-Type
 */
object RequestShapeSuite {
    
    /**
     * Simple data class for request/response.
     */
    data class Item(
        val id: Int? = null,
        val name: String,
        val category: String? = null
    )
    
    /**
     * Result data class for operations.
     */
    data class OperationResult(
        val success: Boolean,
        val message: String
    )
    
    /**
     * Comprehensive service interface demonstrating various request shapes.
     */
    interface RequestShapeService {
        /**
         * Simple GET with path parameter.
         * URL: /items/{id}
         */
        @GET("items/{id}")
        suspend fun getItem(@Path("id") id: Int): Item
        
        /**
         * GET with multiple query parameters.
         * URL: /items?category={category}&limit={limit}&offset={offset}
         */
        @GET("items")
        suspend fun listItems(
            @Query("category") category: String?,
            @Query("limit") limit: Int = 10,
            @Query("offset") offset: Int = 0
        ): List<Item>
        
        /**
         * GET with query map for dynamic parameters.
         * URL: /items?{key1}={value1}&{key2}={value2}...
         */
        @GET("items")
        suspend fun searchItems(@QueryMap filters: Map<String, String>): List<Item>
        
        /**
         * POST with JSON body.
         * Content-Type: application/json
         */
        @POST("items")
        suspend fun createItem(@Body item: Item): Item
        
        /**
         * POST with form-encoded body.
         * Content-Type: application/x-www-form-urlencoded
         */
        @FormUrlEncoded
        @POST("items/form")
        suspend fun createItemForm(
            @Field("name") name: String,
            @Field("category") category: String
        ): OperationResult
        
        /**
         * POST with field map for dynamic form data.
         * Content-Type: application/x-www-form-urlencoded
         */
        @FormUrlEncoded
        @POST("items/form")
        suspend fun createItemFormMap(@FieldMap fields: Map<String, String>): OperationResult
        
        /**
         * PUT with path parameter and body.
         * URL: /items/{id}
         * Content-Type: application/json
         */
        @PUT("items/{id}")
        suspend fun updateItem(@Path("id") id: Int, @Body item: Item): Item
        
        /**
         * DELETE with path parameter.
         * URL: /items/{id}
         */
        @DELETE("items/{id}")
        suspend fun deleteItem(@Path("id") id: Int): OperationResult
        
        /**
         * GET with custom headers.
         */
        @GET("items")
        suspend fun listItemsWithAuth(
            @Header("Authorization") token: String,
            @Header("X-Client-Version") version: String
        ): List<Item>
        
        /**
         * GET with header map for dynamic headers.
         */
        @GET("items")
        suspend fun listItemsWithHeaders(
            @HeaderMap headers: Map<String, String>
        ): List<Item>
        
        /**
         * POST with path, query, header, and body parameters combined.
         * Demonstrates combining multiple parameter types.
         */
        @POST("items/{id}/update")
        suspend fun complexUpdate(
            @Path("id") id: Int,
            @Query("validate") validate: Boolean,
            @Header("X-Request-Source") source: String,
            @Body item: Item
        ): OperationResult
    }
    
    /**
     * Creates a Retrofit instance for request shape testing.
     * 
     * @param baseUrl Base URL for the API
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
     * Creates the service from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return RequestShapeService implementation
     */
    fun createService(retrofit: Retrofit): RequestShapeService {
        return retrofit.create(RequestShapeService::class.java)
    }
}

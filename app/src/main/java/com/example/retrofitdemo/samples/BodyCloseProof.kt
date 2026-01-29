package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * BodyCloseProof: Demonstrates proper response body closing behavior.
 * 
 * Learning nodes:
 * - L4-5: Understanding ResponseBody lifecycle
 * - L4-6: Resource management with response bodies
 * - L4-7: When and how Retrofit closes response bodies
 * 
 * This sample demonstrates:
 * 1. Retrofit automatically closes response bodies in most cases
 * 2. When using Response<T>, body is closed after deserialization
 * 3. When using ResponseBody directly, caller must close it
 * 4. Connection pool depends on proper body closing
 * 5. Memory leaks can occur if ResponseBody is not closed
 * 
 * Key insights:
 * - Retrofit handles closing for you when using T or Response<T>
 * - ResponseBody.string() reads and closes the body
 * - ResponseBody.bytes() reads and closes the body
 * - ResponseBody is one-time use (can't read twice)
 * - Unclosed ResponseBody holds socket connection
 * - OkHttp logs warnings for unclosed bodies
 * 
 * Source reading notes:
 * - CallAdapter wraps responses and manages closing
 * - Response.body() is closed by Retrofit after deserialization
 * - Response.errorBody() must be closed manually if read
 * - ResponseBody.close() releases underlying resources
 * - AutoCloseable interface enables use-with-resources pattern
 */
object BodyCloseProof {
    
    /**
     * Data class for JSON response.
     */
    data class Data(val id: Int, val content: String)
    
    /**
     * Service interface demonstrating different return types.
     */
    interface BodyCloseTestService {
        /**
         * Returns deserialized object.
         * Retrofit automatically closes the response body.
         */
        @GET("data/{id}")
        suspend fun getData(@Path("id") id: Int): Data
        
        /**
         * Returns Response wrapper with deserialized object.
         * Retrofit closes body after deserialization.
         */
        @GET("data/{id}")
        suspend fun getDataResponse(@Path("id") id: Int): Response<Data>
        
        /**
         * Returns raw ResponseBody.
         * Caller MUST close the ResponseBody manually.
         */
        @GET("data/{id}")
        suspend fun getDataRaw(@Path("id") id: Int): ResponseBody
    }
    
    /**
     * Creates a Retrofit instance for body closing tests.
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
     * Demonstrates automatic body closing with direct type.
     * 
     * Retrofit deserializes and closes the body automatically.
     * No manual cleanup needed.
     * 
     * @param service The service instance
     * @param id The data ID to fetch
     * @return The deserialized data
     */
    suspend fun fetchDataAutoClosed(service: BodyCloseTestService, id: Int): Data {
        // Body is automatically closed after deserialization
        return service.getData(id)
    }
    
    /**
     * Demonstrates automatic body closing with Response wrapper.
     * 
     * Retrofit deserializes and closes the body automatically,
     * even when using Response<T> wrapper.
     * 
     * @param service The service instance
     * @param id The data ID to fetch
     * @return The response with deserialized data
     */
    suspend fun fetchDataResponseAutoClosed(
        service: BodyCloseTestService,
        id: Int
    ): Response<Data> {
        // Body is automatically closed after deserialization
        return service.getDataResponse(id)
    }
    
    /**
     * Demonstrates proper manual closing with ResponseBody.
     * 
     * When using ResponseBody directly, caller must close it.
     * This uses use {} block (like try-with-resources in Java).
     * 
     * @param service The service instance
     * @param id The data ID to fetch
     * @return The response body as string
     */
    suspend fun fetchDataManualClose(service: BodyCloseTestService, id: Int): String {
        // Must manually close ResponseBody
        val responseBody = service.getDataRaw(id)
        return responseBody.use { body ->
            // use {} automatically calls close() when block completes
            body.string()
        }
    }
    
    /**
     * Demonstrates reading ResponseBody as string.
     * 
     * string() method reads the entire body and closes it automatically.
     * 
     * @param service The service instance
     * @param id The data ID to fetch
     * @return The response body as string
     */
    suspend fun fetchDataAsString(service: BodyCloseTestService, id: Int): String {
        val responseBody = service.getDataRaw(id)
        // string() reads and closes the body in one operation
        return responseBody.string()
    }
    
    /**
     * Demonstrates reading ResponseBody as bytes.
     * 
     * bytes() method reads the entire body and closes it automatically.
     * 
     * @param service The service instance
     * @param id The data ID to fetch
     * @return The response body as byte array
     */
    suspend fun fetchDataAsBytes(service: BodyCloseTestService, id: Int): ByteArray {
        val responseBody = service.getDataRaw(id)
        // bytes() reads and closes the body in one operation
        return responseBody.bytes()
    }
    
    /**
     * Information about response body closing behavior.
     */
    enum class ClosingBehavior(val description: String) {
        AUTO_CLOSED_TYPE(
            "Using T return type - Retrofit automatically closes body after deserialization"
        ),
        AUTO_CLOSED_RESPONSE(
            "Using Response<T> - Retrofit automatically closes body after deserialization"
        ),
        MANUAL_CLOSE_REQUIRED(
            "Using ResponseBody - Caller must manually close body or use .string()/.bytes()"
        ),
        STRING_AUTO_CLOSES(
            "ResponseBody.string() reads entire body and closes it automatically"
        ),
        BYTES_AUTO_CLOSES(
            "ResponseBody.bytes() reads entire body and closes it automatically"
        ),
        USE_BLOCK_PATTERN(
            "Use responseBody.use { } block to ensure proper closing"
        )
    }
    
    /**
     * Gets all closing behavior descriptions.
     * 
     * @return Map of behavior name to description
     */
    fun getAllClosingBehaviors(): Map<String, String> {
        return ClosingBehavior.values().associate { it.name to it.description }
    }
}

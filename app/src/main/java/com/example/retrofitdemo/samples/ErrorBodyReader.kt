package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * ErrorBodyReader: Demonstrates reading error response bodies.
 * 
 * Learning nodes:
 * - L1-4: Error handling and error body inspection
 * - L3-8: Advanced error response processing
 * 
 * This sample demonstrates:
 * 1. How to read error bodies from failed HTTP responses
 * 2. Difference between successful body (response.body()) and error body (response.errorBody())
 * 3. Parsing structured error messages from APIs
 * 4. Proper resource cleanup when reading error bodies
 * 
 * Key insights:
 * - response.body() is null for non-2xx responses
 * - response.errorBody() contains the error response from server
 * - Error body must be read as string (errorBody().string())
 * - Error body can only be read once (it's consumed)
 * - Always close errorBody if not reading it to prevent resource leaks
 * 
 * Source reading notes:
 * - Response<T> wrapper gives access to both success and error bodies
 * - errorBody() returns ResponseBody which must be closed
 * - You can parse error body JSON manually or with Moshi
 * - Consider using custom CallAdapter for automatic error parsing
 */
object ErrorBodyReader {
    
    /**
     * Data class for successful response.
     */
    data class User(val id: Int, val name: String)
    
    /**
     * Data class for error response from API.
     * Many APIs return structured error information.
     */
    data class ApiError(
        val error: String,
        val message: String,
        val code: Int
    )
    
    /**
     * Service interface using Response<T> wrapper.
     * Response<T> gives access to error bodies.
     */
    interface ErrorTestService {
        @GET("users/{id}")
        suspend fun getUser(@Path("id") id: Int): Response<User>
    }
    
    /**
     * Creates a Retrofit instance for error testing.
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
     * Reads error body as plain string.
     * 
     * This is the simplest approach - just get the raw error message.
     * 
     * @param response The response with error
     * @return Error body as string, or null if no error body
     */
    fun readErrorBodyAsString(response: Response<User>): String? {
        return response.errorBody()?.string()
    }
    
    /**
     * Parses error body as structured ApiError object.
     * 
     * This demonstrates parsing JSON error responses.
     * 
     * @param response The response with error
     * @param moshi Moshi instance for JSON parsing
     * @return Parsed ApiError, or null if parsing fails
     */
    fun readErrorBodyAsObject(response: Response<User>, moshi: Moshi): ApiError? {
        val errorBody = response.errorBody()?.string() ?: return null
        
        return try {
            val adapter = moshi.adapter(ApiError::class.java)
            adapter.fromJson(errorBody)
        } catch (e: Exception) {
            // Parsing failed, return null
            null
        }
    }
    
    /**
     * Extracts error information from response.
     * 
     * This is a convenience method that provides error info in different formats.
     * 
     * @param response The response to inspect
     * @return Map containing error details
     */
    fun extractErrorInfo(response: Response<User>): Map<String, Any?> {
        return mapOf(
            "isSuccessful" to response.isSuccessful,
            "code" to response.code(),
            "message" to response.message(),
            "errorBody" to response.errorBody()?.string(),
            "body" to response.body() // Will be null for errors
        )
    }
}

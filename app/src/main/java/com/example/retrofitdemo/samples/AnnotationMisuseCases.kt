package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

/**
 * AnnotationMisuseCases: Demonstrates common annotation mistakes.
 * 
 * Learning nodes:
 * - L3-8: Common Retrofit annotation pitfalls
 * - L3-9: Understanding annotation validation
 * - L4-1: Compile-time vs runtime annotation errors
 * 
 * This sample demonstrates:
 * 1. Common annotation mistakes developers make
 * 2. How Retrofit validates annotations
 * 3. Understanding which errors are caught at compile-time vs runtime
 * 4. Proper annotation usage patterns
 * 
 * Key insights:
 * - Missing @FormUrlEncoded causes IllegalArgumentException
 * - @Body with @FormUrlEncoded is invalid
 * - @Path requires matching placeholder in URL
 * - Multiple @Body parameters are not allowed
 * - @Query cannot be used with @Body for the same parameter
 * 
 * Source reading notes:
 * - ServiceMethod.parseAnnotations() validates method annotations
 * - ParameterHandler validates parameter annotations
 * - RequestBuilder enforces annotation compatibility rules
 * - Most errors are detected lazily on first method call
 * 
 * NOTE: This file contains CORRECT examples that explain common mistakes.
 * The mistakes themselves would cause compilation or runtime errors.
 */
object AnnotationMisuseCases {
    
    data class User(val id: Int? = null, val name: String, val email: String)
    data class Response(val success: Boolean, val message: String)
    
    /**
     * CORRECT: Proper form-encoded request.
     * Common mistake: Forgetting @FormUrlEncoded annotation.
     */
    interface CorrectFormService {
        @FormUrlEncoded
        @POST("users")
        suspend fun createUser(
            @Field("name") name: String,
            @Field("email") email: String
        ): Response
    }
    
    /**
     * CORRECT: Using @Body for JSON request.
     * Common mistake: Mixing @Body with @Field or @FormUrlEncoded.
     */
    interface CorrectBodyService {
        @POST("users")
        suspend fun createUser(@Body user: User): Response
    }
    
    /**
     * CORRECT: Path parameter matches URL placeholder.
     * Common mistake: Mismatch between @Path value and URL placeholder.
     */
    interface CorrectPathService {
        @GET("users/{id}")
        suspend fun getUser(@Path("id") id: Int): User
        
        // Correct: Multiple path parameters
        @GET("users/{userId}/posts/{postId}")
        suspend fun getUserPost(
            @Path("userId") userId: Int,
            @Path("postId") postId: Int
        ): Response
    }
    
    /**
     * CORRECT: Single body parameter.
     * Common mistake: Multiple @Body parameters in same method.
     */
    interface CorrectSingleBodyService {
        @POST("users")
        suspend fun createUser(@Body user: User): Response
    }
    
    /**
     * CORRECT: Mixing different parameter types appropriately.
     * Common mistake: Incompatible parameter type combinations.
     */
    interface CorrectMixedParametersService {
        // Correct: Body with path and query parameters
        @PUT("users/{id}")
        suspend fun updateUser(
            @Path("id") id: Int,
            @Query("notify") notify: Boolean,
            @Body user: User
        ): Response
        
        // Correct: Header with body
        @POST("users")
        suspend fun createUserWithAuth(
            @Header("Authorization") token: String,
            @Body user: User
        ): Response
    }
    
    /**
     * CORRECT: Optional query parameters.
     * Common mistake: Not making optional parameters nullable.
     */
    interface CorrectOptionalParametersService {
        @GET("users")
        suspend fun searchUsers(
            @Query("name") name: String?,
            @Query("email") email: String?,
            @Query("limit") limit: Int = 10
        ): List<User>
    }
    
    /**
     * Validation utilities to demonstrate error detection.
     */
    
    /**
     * Creates a Retrofit instance for testing annotations.
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
     * Attempts to create a service and returns whether it succeeded.
     * 
     * This helps demonstrate that service creation itself usually succeeds,
     * but validation happens on first method call (lazy validation).
     * 
     * @param retrofit The Retrofit instance
     * @param serviceClass The service interface class
     * @return True if service creation succeeded
     */
    fun <T> canCreateService(retrofit: Retrofit, serviceClass: Class<T>): Boolean {
        return try {
            retrofit.create(serviceClass)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Common mistake patterns to avoid (documented, not implemented).
     */
    enum class CommonMistake(val description: String, val solution: String) {
        MISSING_FORM_ENCODED(
            description = "Using @Field without @FormUrlEncoded",
            solution = "Add @FormUrlEncoded annotation to method"
        ),
        BODY_WITH_FORM(
            description = "Mixing @Body with @FormUrlEncoded",
            solution = "Use either @Body or @Field/@FormUrlEncoded, not both"
        ),
        PATH_MISMATCH(
            description = "@Path value doesn't match URL placeholder",
            solution = "Ensure @Path(\"name\") matches {name} in URL"
        ),
        MULTIPLE_BODIES(
            description = "Multiple @Body parameters in same method",
            solution = "Use only one @Body parameter per method"
        ),
        BODY_WITH_FIELD(
            description = "Mixing @Body with @Field in same method",
            solution = "Use @Body for JSON or @Field for form-encoded, not both"
        ),
        REQUIRED_QUERY_NOT_NULLABLE(
            description = "Optional query parameter not marked nullable",
            solution = "Use nullable types (String?) for optional parameters"
        );
        
        fun getAdvice(): String = "$description. Solution: $solution"
    }
    
    /**
     * Gets advice for all common mistakes.
     * 
     * @return Map of mistake name to advice
     */
    fun getAllMistakeAdvice(): Map<String, String> {
        return CommonMistake.values().associate { it.name to it.getAdvice() }
    }
}

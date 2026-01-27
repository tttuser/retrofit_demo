package com.example.retrofitdemo.network

import com.example.retrofitdemo.model.LoginResponse
import com.example.retrofitdemo.model.Post
import com.example.retrofitdemo.model.User
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

/**
 * ApiService: Defines the API endpoints for the application.
 * 
 * This interface demonstrates various Retrofit features:
 * 
 * 1. Path parameters (@Path): Substitute values directly into the URL path
 * 2. Query parameters (@Query): Add query string parameters to the URL
 * 3. Response<T>: Wraps the response to access HTTP metadata (status code, headers, etc.)
 * 4. Call<T>: Lazy call that can be executed synchronously or asynchronously
 * 5. Form-encoded requests (@FormUrlEncoded, @Field): Send data as application/x-www-form-urlencoded
 * 
 * Source reading notes:
 * - Retrofit generates implementation at runtime using dynamic proxies
 * - Each method maps to a specific HTTP request
 * - Return types determine how Retrofit handles the call:
 *   - Call<T>: Returns a Call object that must be executed
 *   - Response<T>: Executes immediately and returns full HTTP response
 *   - T: Executes immediately and returns only the body (throws on error)
 */
interface ApiService {
    
    /**
     * Demonstrates path and query parameters.
     * GET /users/{userId}?includeEmail={true/false}
     * 
     * @param userId The user ID to fetch (path parameter)
     * @param includeEmail Whether to include email in response (query parameter)
     * @return User object
     */
    @GET("users/{userId}")
    suspend fun getUser(
        @Path("userId") userId: Int,
        @Query("includeEmail") includeEmail: Boolean = true
    ): User
    
    /**
     * Demonstrates Response<T> wrapper for accessing HTTP metadata.
     * GET /users/{userId}
     * 
     * Response<T> allows you to:
     * - Check HTTP status code (response.code())
     * - Access response headers (response.headers())
     * - Handle both success and error cases (response.isSuccessful)
     * - Get error body for non-2xx responses (response.errorBody())
     * 
     * @param userId The user ID to fetch
     * @return Response wrapper containing User or error details
     */
    @GET("users/{userId}")
    suspend fun getUserWithResponse(
        @Path("userId") userId: Int
    ): Response<User>
    
    /**
     * Demonstrates Call<T> for lazy execution.
     * GET /posts
     * 
     * Call<T> provides:
     * - Lazy execution (call.execute() or call.enqueue())
     * - Ability to cancel the request (call.cancel())
     * - Can be cloned for retry logic (call.clone())
     * - Used with custom CallAdapter (ApiResultCallAdapterFactory)
     * 
     * @return Call object that will return a list of posts when executed
     */
    @GET("posts")
    fun getPosts(): Call<List<Post>>
    
    /**
     * Demonstrates form-encoded POST request.
     * POST /auth/login with application/x-www-form-urlencoded body
     * 
     * @FormUrlEncoded annotation indicates the request body will be form-encoded
     * @Field parameters are encoded as key=value pairs
     * 
     * Form-encoded vs JSON:
     * - Form-encoded: application/x-www-form-urlencoded (like HTML forms)
     * - JSON: application/json (more common for modern APIs)
     * 
     * @param username The username for login
     * @param password The password for login
     * @return Call object that will return login response
     */
    @FormUrlEncoded
    @POST("auth/login")
    fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<LoginResponse>
    
    /**
     * Demonstrates using Call with custom CallAdapter (ApiResultCallAdapterFactory).
     * The CallAdapter wraps the result in ApiResult<T> for better error handling.
     * GET /users/{userId}
     * 
     * @param userId The user ID to fetch
     * @return Call that returns ApiResult wrapping User
     */
    @GET("users/{userId}")
    fun getUserAsApiResult(
        @Path("userId") userId: Int
    ): Call<ApiResult<User>>
}

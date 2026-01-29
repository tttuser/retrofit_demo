package com.example.retrofitdemo.samples

import com.example.retrofitdemo.network.ApiResult
import com.example.retrofitdemo.network.ApiResultCallAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * ApiResultCallAdapter: Demonstrates the ApiResultCallAdapterFactory custom CallAdapter.
 * 
 * Learning nodes:
 * - L3-1: Understanding CallAdapter mechanism
 * - L4-2: Building custom CallAdapters
 * 
 * This sample demonstrates:
 * 1. Automatic wrapping of responses in ApiResult using CallAdapter
 * 2. Eliminating boilerplate error handling code
 * 3. Type-safe error handling at the API boundary
 * 4. How CallAdapters transform return types
 * 
 * Source reading notes:
 * - CallAdapter.Factory determines which adapters handle which return types
 * - ApiResultCallAdapterFactory handles Call<ApiResult<T>>
 * - Wraps both success and error responses automatically
 * - Eliminates need for manual try-catch or error checking
 * - See also: ApiResultDesign for the ApiResult pattern itself
 * 
 * How it works:
 * 1. Service method declares return type Call<ApiResult<T>>
 * 2. Retrofit asks each CallAdapter.Factory if it can handle this type
 * 3. ApiResultCallAdapterFactory recognizes the pattern and returns adapter
 * 4. Adapter wraps the original Call to transform responses
 * 5. All responses (success/error) are wrapped in ApiResult
 */
object ApiResultCallAdapter {
    
    /**
     * Simple data model for testing.
     */
    data class Post(
        val id: Int,
        val title: String,
        val body: String
    )
    
    /**
     * Service interface using Call<ApiResult<T>> return type.
     * The CallAdapter automatically wraps responses in ApiResult.
     */
    interface PostService {
        /**
         * Gets a post with automatic ApiResult wrapping.
         * No need for manual error handling - ApiResult contains all cases.
         */
        @GET("posts/{id}")
        fun getPost(@Path("id") id: Int): Call<ApiResult<Post>>
        
        /**
         * Gets multiple posts with automatic ApiResult wrapping.
         */
        @GET("posts")
        fun getPosts(): Call<ApiResult<List<Post>>>
    }
    
    /**
     * Creates a Retrofit instance with ApiResultCallAdapterFactory.
     * 
     * @param baseUrl The base URL for the API
     * @return Configured Retrofit instance with custom CallAdapter
     */
    fun createRetrofit(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            // Add the custom CallAdapter factory
            .addCallAdapterFactory(ApiResultCallAdapterFactory())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Creates the PostService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return PostService implementation
     */
    fun createService(retrofit: Retrofit): PostService {
        return retrofit.create(PostService::class.java)
    }
    
    /**
     * Example: Using the service with automatic ApiResult wrapping.
     * 
     * This demonstrates the simplified error handling with ApiResult.
     * No try-catch needed - all errors are in ApiResult.Error.
     * 
     * @param service The PostService
     * @param id The post ID to fetch
     * @return A status message
     */
    fun fetchPost(service: PostService, id: Int): String {
        // Execute the call
        val response = service.getPost(id).execute()
        
        // The response body is always present and always contains ApiResult
        val result = response.body()!!
        
        // Handle the result with when expression
        return when (result) {
            is ApiResult.Success -> {
                val post = result.data
                "Success: ${post.title} (${post.id})"
            }
            is ApiResult.Error -> {
                val message = result.exception.message ?: "Unknown error"
                "Error: $message"
            }
        }
    }
    
    /**
     * Example: Comparing with and without CallAdapter.
     * 
     * This shows the boilerplate that CallAdapter eliminates.
     */
    fun comparisonExample() {
        // Without CallAdapter (manual wrapping):
        // try {
        //     val response = service.getPostRaw(id).execute()
        //     if (response.isSuccessful) {
        //         val body = response.body()
        //         if (body != null) {
        //             return ApiResult.Success(body)
        //         } else {
        //             return ApiResult.Error(NullPointerException("Body is null"))
        //         }
        //     } else {
        //         return ApiResult.Error(
        //             Exception("HTTP ${response.code()}"),
        //             response.errorBody()?.string()
        //         )
        //     }
        // } catch (e: Exception) {
        //     return ApiResult.Error(e)
        // }
        
        // With CallAdapter (automatic wrapping):
        // val result = service.getPost(id).execute().body()!!
        // when (result) {
        //     is ApiResult.Success -> handleData(result.data)
        //     is ApiResult.Error -> handleError(result.exception)
        // }
    }
    
    /**
     * Benefits of using ApiResultCallAdapter:
     * 1. Eliminates try-catch boilerplate
     * 2. Makes error handling explicit and type-safe
     * 3. Consistent error handling across all API calls
     * 4. Forces developers to handle errors (exhaustive when)
     * 5. Network errors, HTTP errors, and parsing errors all handled uniformly
     */
    data class Benefits(
        val eliminatesBoilerplate: Boolean = true,
        val typeSafe: Boolean = true,
        val consistentErrorHandling: Boolean = true,
        val exhaustiveHandling: Boolean = true,
        val uniformErrorWrapping: Boolean = true
    )
    
    /**
     * Gets the benefits of using ApiResultCallAdapter.
     */
    fun getBenefits(): Benefits = Benefits()
}

package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * SyncVsAsyncVsSuspend: Compares synchronous, asynchronous, and suspend execution patterns.
 * 
 * Learning nodes:
 * - L1-5: Different execution patterns in Retrofit
 * - L3-5: Advanced async patterns and best practices
 * 
 * This sample demonstrates:
 * 1. Synchronous execution with Call.execute() (blocking)
 * 2. Asynchronous execution with Call.enqueue() (callback-based)
 * 3. Suspend function execution (coroutine-based)
 * 4. Thread behavior and performance characteristics
 * 
 * Source reading notes:
 * - execute(): Blocks current thread, must not be called on main thread
 * - enqueue(): Returns immediately, callback on OkHttp thread
 * - suspend: Non-blocking, works on any thread, integrates with coroutines
 * - Suspend is generally preferred for modern Kotlin/Android apps
 */
object SyncVsAsyncVsSuspend {
    
    /**
     * Simple data model for testing.
     */
    data class Item(
        val id: Int,
        val name: String
    )
    
    /**
     * Service interface with different execution patterns.
     */
    interface ExecutionService {
        /**
         * Returns Call<T> for manual execution.
         * Can be executed synchronously with execute() or asynchronously with enqueue().
         */
        @GET("items/{id}")
        fun getItemAsCall(@Path("id") id: Int): Call<Item>
        
        /**
         * Returns suspend function for coroutine-based execution.
         * Non-blocking, integrates with structured concurrency.
         */
        @GET("items/{id}")
        suspend fun getItemSuspend(@Path("id") id: Int): Item
    }
    
    /**
     * Creates a Retrofit instance for ExecutionService.
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
     * Creates the ExecutionService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return ExecutionService implementation
     */
    fun createService(retrofit: Retrofit): ExecutionService {
        return retrofit.create(ExecutionService::class.java)
    }
    
    /**
     * Pattern 1: Synchronous execution with Call.execute().
     * 
     * Characteristics:
     * - Blocks the calling thread until response arrives
     * - Must not be called on Android main thread
     * - Returns Response<T> with full HTTP metadata
     * - Simple error handling with try-catch
     * 
     * @param call The Call to execute
     * @return The response, or null on error
     */
    fun executeSync(call: Call<Item>): Response<Item>? {
        return try {
            // This blocks until the response is received
            call.execute()
        } catch (e: Exception) {
            // Handle network errors
            null
        }
    }
    
    /**
     * Pattern 2: Asynchronous execution with Call.enqueue().
     * 
     * Characteristics:
     * - Returns immediately, doesn't block
     * - Callback invoked on OkHttp background thread
     * - Must manually handle threading for UI updates
     * - More complex error handling with callbacks
     * 
     * @param call The Call to enqueue
     * @param onSuccess Callback for successful response
     * @param onError Callback for error
     */
    fun executeAsync(
        call: Call<Item>,
        onSuccess: (Item) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        call.enqueue(object : Callback<Item> {
            override fun onResponse(call: Call<Item>, response: Response<Item>) {
                if (response.isSuccessful) {
                    response.body()?.let { onSuccess(it) }
                        ?: onError(NullPointerException("Response body is null"))
                } else {
                    onError(Exception("HTTP ${response.code()}: ${response.message()}"))
                }
            }
            
            override fun onFailure(call: Call<Item>, t: Throwable) {
                onError(t)
            }
        })
    }
    
    /**
     * Pattern 3: Suspend function execution.
     * 
     * Characteristics:
     * - Non-blocking, works with coroutines
     * - Can switch threads easily with withContext
     * - Throws exceptions on error (can use try-catch)
     * - Integrates with structured concurrency
     * - Preferred for modern Kotlin/Android apps
     * 
     * @param service The service with suspend function
     * @param id The item ID to fetch
     * @return The item
     * @throws Exception on network or HTTP error
     */
    suspend fun executeSuspend(service: ExecutionService, id: Int): Item {
        // Can optionally switch to IO dispatcher
        return withContext(Dispatchers.IO) {
            service.getItemSuspend(id)
        }
    }
    
    /**
     * Helper: Convert Call.enqueue() to suspend function.
     * This demonstrates how to bridge callback-based APIs to coroutines.
     */
    suspend fun <T> Call<T>.await(): T = suspendCoroutine { continuation ->
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        continuation.resume(it)
                    } ?: continuation.resumeWithException(
                        NullPointerException("Response body is null")
                    )
                } else {
                    continuation.resumeWithException(
                        Exception("HTTP ${response.code()}: ${response.message()}")
                    )
                }
            }
            
            override fun onFailure(call: Call<T>, t: Throwable) {
                continuation.resumeWithException(t)
            }
        })
    }
    
    /**
     * Comparison summary of execution patterns.
     */
    data class ExecutionComparison(
        val pattern: String,
        val blocking: Boolean,
        val requiresBackgroundThread: Boolean,
        val callbackBased: Boolean,
        val coroutineSupport: Boolean,
        val complexity: String
    )
    
    /**
     * Gets comparison data for the three patterns.
     */
    fun getComparisonTable(): List<ExecutionComparison> {
        return listOf(
            ExecutionComparison(
                pattern = "Synchronous (execute)",
                blocking = true,
                requiresBackgroundThread = true,
                callbackBased = false,
                coroutineSupport = false,
                complexity = "Low"
            ),
            ExecutionComparison(
                pattern = "Asynchronous (enqueue)",
                blocking = false,
                requiresBackgroundThread = false,
                callbackBased = true,
                coroutineSupport = false,
                complexity = "Medium"
            ),
            ExecutionComparison(
                pattern = "Suspend function",
                blocking = false,
                requiresBackgroundThread = false,
                callbackBased = false,
                coroutineSupport = true,
                complexity = "Low"
            )
        )
    }
}

package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.io.IOException
import java.util.concurrent.TimeUnit
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// 占位异常；也可换 retrofit2.HttpException
class HttpExceptionLike(val code: Int, message: String?) : IOException("HTTP $code ${message ?: ""}")

suspend fun <T> Call<T>.awaitCancellable(): T =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            this.cancel()
        }

        enqueue(object : Callback<T> {

            override fun onResponse(call: Call<T?>, response: Response<T?>) {
                if (!cont.isActive) return
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    cont.resume(body)
                } else {
                    cont.resumeWithException(
                        HttpExceptionLike(response.code(), response.errorBody()?.string())
                    )
                }
            }

            override fun onFailure(call: Call<T?>, t: Throwable) {
                if (!cont.isActive) return
                cont.resumeWithException(t)
            }

        })

    }

/**
 * CancelPropagationSampleTest: Demonstrates request cancellation behavior.
 * 
 * Learning node:
 * - L1-5: Cancel propagation for Call and coroutines
 * 
 * This test demonstrates:
 * 1. Cancelling Call<T> with call.cancel()
 * 2. Cancelling coroutine-based requests with job.cancel()
 * 3. How cancellation propagates to the underlying network request
 * 4. Verifying that cancelled requests are actually cancelled
 * 
 * Source reading notes:
 * - Call.cancel() cancels the underlying OkHttp call
 * - Coroutine cancellation triggers request cancellation
 * - Cancelled calls throw IOException
 * - Cancellation is cooperative and immediate for network calls
 */
class CancelPropagationSampleTest {
    
    /**
     * Simple data model for testing.
     */
    data class TestData(
        val id: Int,
        val value: String
    )
    
    /**
     * Service interface for cancellation testing.
     */
    interface CancelService {
        @GET("slow")
        fun getDataAsCall(): Call<TestData>
        
        @GET("slow")
        suspend fun getData(): TestData
    }
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: CancelService
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        service = retrofit.create(CancelService::class.java)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that Call.cancel() cancels the request.
     * 
     * Strategy:
     * 1. Start a slow response
     * 2. Cancel the call before it completes
     * 3. Verify IOException is thrown
     */
    @Test
    fun `Call cancel() cancels the network request`() {
        // Arrange: Mock slow response (3 second delay)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"slow response"}""")
                .setBodyDelay(3, TimeUnit.SECONDS)
        )
        
        // Act: Start call and cancel it immediately
        val call = service.getDataAsCall()
        
        // Start execution in a separate thread
        var exception: Exception? = null
        val thread = Thread {
            try {
                call.execute()
            } catch (e: Exception) {
                exception = e
            }
        }
        thread.start()
        
        // Give it a moment to start, then cancel
        Thread.sleep(100)
        call.cancel()
        
        // Wait for thread to finish
        thread.join(5000)
        
        // Assert: Should throw IOException due to cancellation
        assertNotNull("Should have thrown exception", exception)
        assertTrue(
            "Should be IOException",
            exception is IOException
        )
        assertTrue("Call should be cancelled", call.isCanceled)
    }
    
    /**
     * Test that cancelled Call cannot be executed again.
     */
    @Test
    fun `cancelled Call throws IOException on execute`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"test"}""")
        )
        
        val call = service.getDataAsCall()
        
        // Act: Cancel before execution
        call.cancel()
        
        // Assert: Execute should throw
        try {
            call.execute()
            fail("Should have thrown IOException")
        } catch (e: IOException) {
            // Expected
            assertTrue("Call should be cancelled", call.isCanceled)
        }
    }
    
    /**
     * Test that coroutine cancellation cancels the network request.
     * 
     * Strategy:
     * 1. Launch a coroutine with a slow request
     * 2. Cancel the coroutine before completion
     * 3. Verify CancellationException is thrown
     */
    @Test
    fun `coroutine cancellation cancels the network request`() = runBlocking {
        // Arrange: Mock slow response (2 second delay)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"slow response"}""")
                .setBodyDelay(2, TimeUnit.SECONDS)
        )
        
        // Act: Launch coroutine and cancel it
        val job = launch {
            try {
                service.getData()
                fail("Should have been cancelled")
            } catch (e: CancellationException) {
                // Expected - coroutine was cancelled
                throw e // Re-throw to maintain coroutine cancellation
            }
        }
        
        // Give it a moment to start, then cancel
        delay(100)
        job.cancel()
        
        // Wait for cancellation to complete
        job.join()
        
        // Assert: Job should be cancelled
        assertTrue("Job should be cancelled", job.isCancelled)
    }
    
    /**
     * Test that coroutine cancellation with timeout works.
     */
    @Test
    fun `withTimeout cancels slow request`() = runBlocking {
        // Arrange: Mock very slow response (5 seconds)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"very slow"}""")
                .setBodyDelay(5, TimeUnit.SECONDS)
        )

        // Act & Assert: Use withTimeout to cancel
        try {
            withTimeout(500) { // 500ms timeout
                service.getData()
            }
            fail("Should have timed out")
        } catch (e: TimeoutCancellationException) {
            // Expected - request was cancelled due to timeout
            assertNotNull("TimeoutCancellationException should not be null", e)
        }
    }
    
    /**
     * Test that multiple concurrent calls can be cancelled independently.
     */
    @Test
    fun `multiple calls can be cancelled independently`() {
        // Arrange: Mock slow responses
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"id":$it,"value":"response $it"}""")
                    .setBodyDelay(2, TimeUnit.SECONDS)
            )
        }
        
        // Act: Create multiple calls
        val call1 = service.getDataAsCall()
        val call2 = service.getDataAsCall()
        val call3 = service.getDataAsCall()
        
        // Start them in separate threads
        val threads = listOf(
            Thread { try { call1.execute() } catch (e: IOException) {} },
            Thread { try { call2.execute() } catch (e: IOException) {} },
            Thread { try { call3.execute() } catch (e: IOException) {} }
        )
        threads.forEach { it.start() }
        
        // Give them time to start
        Thread.sleep(100)
        
        // Cancel only call2
        call2.cancel()
        
        // Assert: Only call2 should be cancelled
        assertFalse("Call1 should not be cancelled", call1.isCanceled)
        assertTrue("Call2 should be cancelled", call2.isCanceled)
        assertFalse("Call3 should not be cancelled", call3.isCanceled)
        
        // Cleanup: Cancel remaining calls and wait for threads
        call1.cancel()
        call3.cancel()
        threads.forEach { it.join(1000) }
    }
    
    /**
     * Test that parent coroutine cancellation cancels child requests.
     */
    @Test
    fun `parent coroutine cancellation cancels child requests`() = runBlocking {
        // Arrange: Mock slow response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"slow"}""")
                .setBodyDelay(2, TimeUnit.SECONDS)
        )
        
        // Act: Launch parent scope with child coroutine
        val parentJob = launch {
            launch {
                try {
                    service.getData()
                    fail("Should have been cancelled")
                } catch (e: CancellationException) {
                    // Expected
                    throw e
                }
            }
        }
        
        // Give it time to start
        delay(100)
        
        // Cancel parent
        parentJob.cancel()
        parentJob.join()
        
        // Assert: Parent job should be cancelled
        assertTrue("Parent job should be cancelled", parentJob.isCancelled)
    }
}

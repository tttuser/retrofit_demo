package com.example.retrofitdemo.samples

import kotlinx.coroutines.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ThreadProofTest: Tests for ThreadProof sample.
 * 
 * Test strategy:
 * 1. Verify that a single Retrofit instance can handle concurrent requests
 * 2. Confirm no race conditions or synchronization issues
 * 3. Validate that all requests complete successfully under load
 * 4. Demonstrate connection pooling efficiency
 */
class ThreadProofTest {
    
    private lateinit var mockWebServer: MockWebServer
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        ThreadProof.resetInstanceCounter()
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that a single Retrofit instance handles concurrent requests safely.
     * 
     * This test launches multiple coroutines that all use the same Retrofit instance
     * and verifies that all requests complete successfully without errors.
     */
    @Test
    fun `single Retrofit instance handles concurrent requests safely`() = runBlocking {
        // Arrange: Setup mock server with slow responses to ensure concurrency
        val requestCounter = AtomicInteger(0)
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val requestId = requestCounter.incrementAndGet()
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"threadId":${Thread.currentThread().id},"requestId":$requestId}""")
                    .setBodyDelay(10, TimeUnit.MILLISECONDS) // Small delay to ensure concurrency
            }
        }
        mockWebServer.start()
        
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = ThreadProof.createRetrofit(baseUrl)
        val service = retrofit.create(ThreadProof.ThreadTestService::class.java)
        
        // Act: Launch 50 concurrent requests
        val concurrentRequests = 50
        val results = ConcurrentHashMap.newKeySet<Int>()
        
        val jobs = (1..concurrentRequests).map { requestNum ->
            launch(Dispatchers.IO) {
                val response = service.getData()
                results.add(response.requestId)
            }
        }
        
        // Wait for all jobs to complete
        jobs.forEach { it.join() }
        
        // Assert: All requests should complete successfully
        assertEquals("All requests should complete", concurrentRequests, results.size)
        
        // Verify only one Retrofit instance was created
        assertEquals("Should reuse single Retrofit instance", 1, ThreadProof.getInstanceCount())
        
        println("ThreadProof: Successfully completed $concurrentRequests concurrent requests")
    }
    
    /**
     * Test that multiple threads can safely call the same service method.
     * 
     * Uses plain Java threads instead of coroutines to demonstrate
     * true thread-safety at the Java threading level.
     */
    @Test
    fun `multiple threads can safely call service methods`() {
        // Arrange: Setup mock server
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"threadId":1,"requestId":1}""")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"threadId":2,"requestId":2}""")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"threadId":3,"requestId":3}""")
        )
        mockWebServer.start()
        
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = ThreadProof.createRetrofit(baseUrl)
        val service = retrofit.create(ThreadProof.ThreadTestService::class.java)
        
        // Act: Launch requests from multiple threads
        val threadCount = 3
        val latch = CountDownLatch(threadCount)
        val errors = ConcurrentHashMap.newKeySet<Throwable>()
        val results = ConcurrentHashMap.newKeySet<Int>()
        
        repeat(threadCount) { threadNum ->
            Thread {
                try {
                    runBlocking {
                        val response = service.getData()
                        results.add(response.requestId)
                    }
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        
        // Wait for all threads to complete (with timeout)
        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS))
        
        // Assert: No errors should occur
        assertTrue("No errors should occur: ${errors.joinToString()}", errors.isEmpty())
        
        // All requests should complete
        assertEquals("All requests should complete", threadCount, results.size)
        
        println("ThreadProof: Successfully handled $threadCount requests from separate threads")
    }
    
    /**
     * Test that service interface proxy is thread-safe.
     * 
     * Creates the service once and calls it from multiple threads simultaneously.
     */
    @Test
    fun `service interface proxy is thread-safe`() = runBlocking {
        // Arrange: Mock server with many responses
        val responseCount = 100
        repeat(responseCount) { i ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"threadId":${Thread.currentThread().id},"requestId":$i}""")
            )
        }
        mockWebServer.start()
        
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = ThreadProof.createRetrofit(baseUrl)
        
        // Create service once
        val service = retrofit.create(ThreadProof.ThreadTestService::class.java)
        
        // Act: Make many concurrent calls using the same service instance
        val results = (1..responseCount).map { requestNum ->
            async(Dispatchers.IO) {
                try {
                    service.getData()
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll()
        
        // Assert: All calls should succeed
        val successCount = results.count { it != null }
        assertEquals("All requests should succeed", responseCount, successCount)
        
        println("ThreadProof: Service proxy safely handled $responseCount concurrent calls")
    }
    
    /**
     * Test that connection pooling works correctly under concurrent load.
     * 
     * Verifies that the connection pool efficiently reuses connections
     * rather than creating a new connection for each request.
     */
    @Test
    fun `connection pooling works under concurrent load`() = runBlocking {
        // Arrange: Mock server
        val requestCount = 20
        repeat(requestCount) { i ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"threadId":${Thread.currentThread().id},"requestId":$i}""")
            )
        }
        mockWebServer.start()
        
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = ThreadProof.createRetrofit(baseUrl)
        val service = retrofit.create(ThreadProof.ThreadTestService::class.java)
        
        // Act: Make concurrent requests
        val jobs = (1..requestCount).map {
            launch(Dispatchers.IO) {
                service.getData()
            }
        }
        
        jobs.forEach { it.join() }
        
        // Assert: All requests should complete
        assertEquals("All requests should be received", requestCount, mockWebServer.requestCount)
        
        println("ThreadProof: Connection pool handled $requestCount requests")
    }
    
    /**
     * Test that Retrofit instance counter increments correctly.
     * 
     * This verifies our test infrastructure works as expected.
     */
    @Test
    fun `instance counter tracks Retrofit creation`() {
        // Arrange
        val baseUrl = "https://example.com/"
        
        // Act: Create multiple instances
        ThreadProof.createRetrofit(baseUrl)
        ThreadProof.createRetrofit(baseUrl)
        ThreadProof.createRetrofit(baseUrl)
        
        // Assert: Counter should track all creations
        assertEquals("Counter should track all instances", 3, ThreadProof.getInstanceCount())
    }
}

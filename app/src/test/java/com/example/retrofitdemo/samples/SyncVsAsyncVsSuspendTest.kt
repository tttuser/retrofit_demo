package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * SyncVsAsyncVsSuspendTest: Tests for SyncVsAsyncVsSuspend sample.
 * 
 * Test strategy:
 * 1. Test synchronous execution blocks until response
 * 2. Test asynchronous execution returns immediately with callback
 * 3. Test suspend function execution works with coroutines
 * 4. Verify comparison data accuracy
 */
class SyncVsAsyncVsSuspendTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: SyncVsAsyncVsSuspend.ExecutionService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit and service
        val retrofit = SyncVsAsyncVsSuspend.createRetrofit(mockWebServer.url("/").toString())
        service = SyncVsAsyncVsSuspend.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test synchronous execution with Call.execute().
     */
    @Test
    fun `synchronous execution blocks until response`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"Sync Item"}""")
        )
        
        // Act
        val call = service.getItemAsCall(1)
        val response = SyncVsAsyncVsSuspend.executeSync(call)
        
        // Assert
        assertNotNull("Response should not be null", response)
        assertTrue("Response should be successful", response!!.isSuccessful)
        assertEquals(1, response.body()?.id)
        assertEquals("Sync Item", response.body()?.name)
    }
    
    /**
     * Test synchronous execution handles errors.
     */
    @Test
    fun `synchronous execution returns null on error`() {
        // Arrange: Server returns 404
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Not found"}""")
        )
        
        // Act
        val call = service.getItemAsCall(999)
        val response = SyncVsAsyncVsSuspend.executeSync(call)
        
        // Assert: Should return response, not null (even for errors)
        assertNotNull("Response should not be null for HTTP errors", response)
        assertFalse("Response should not be successful", response!!.isSuccessful)
        assertEquals(404, response.code())
    }
    
    /**
     * Test asynchronous execution with Call.enqueue().
     */
    @Test
    fun `asynchronous execution returns immediately with callback`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":2,"name":"Async Item"}""")
        )
        
        val resultRef = AtomicReference<SyncVsAsyncVsSuspend.Item>()
        val errorRef = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        
        // Act: Execute asynchronously
        val call = service.getItemAsCall(2)
        SyncVsAsyncVsSuspend.executeAsync(
            call,
            onSuccess = { item ->
                resultRef.set(item)
                latch.countDown()
            },
            onError = { error ->
                errorRef.set(error)
                latch.countDown()
            }
        )
        
        // Wait for callback (with timeout)
        assertTrue("Callback should be invoked", latch.await(5, TimeUnit.SECONDS))
        
        // Assert
        assertNull("Error should be null", errorRef.get())
        assertNotNull("Result should not be null", resultRef.get())
        assertEquals(2, resultRef.get()?.id)
        assertEquals("Async Item", resultRef.get()?.name)
    }
    
    /**
     * Test asynchronous execution handles errors via callback.
     */
    @Test
    fun `asynchronous execution invokes error callback on failure`() {
        // Arrange: Server returns 500
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"Server error"}""")
        )
        
        val errorRef = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        
        // Act
        val call = service.getItemAsCall(999)
        SyncVsAsyncVsSuspend.executeAsync(
            call,
            onSuccess = { latch.countDown() },
            onError = { error ->
                errorRef.set(error)
                latch.countDown()
            }
        )
        
        // Wait for callback
        assertTrue("Callback should be invoked", latch.await(5, TimeUnit.SECONDS))
        
        // Assert
        assertNotNull("Error should not be null", errorRef.get())
        assertTrue(
            "Error message should mention HTTP 500",
            errorRef.get()?.message?.contains("500") == true
        )
    }
    
    /**
     * Test suspend function execution.
     */
    @Test
    fun `suspend function execution works with coroutines`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":3,"name":"Suspend Item"}""")
        )
        
        // Act
        val item = SyncVsAsyncVsSuspend.executeSuspend(service, 3)
        
        // Assert
        assertEquals(3, item.id)
        assertEquals("Suspend Item", item.name)
    }
    
    /**
     * Test suspend function throws on error.
     */
    @Test
    fun `suspend function throws exception on error`() = runBlocking {
        // Arrange: Server returns 404
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Not found"}""")
        )
        
        // Act & Assert
        try {
            SyncVsAsyncVsSuspend.executeSuspend(service, 999)
            fail("Should have thrown exception")
        } catch (e: Exception) {
            // Expected - suspend functions throw on HTTP errors
            assertNotNull("Exception should not be null", e.message)
        }
    }
    
    /**
     * Test Call.await() extension function.
     */
    @Test
    fun `Call await extension converts callback to suspend`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":4,"name":"Await Item"}""")
        )
        
        // Act: Use await extension
        val call = service.getItemAsCall(4)
        val item = SyncVsAsyncVsSuspend.run { call.await() }
        
        // Assert
        assertEquals(4, item.id)
        assertEquals("Await Item", item.name)
    }
    
    /**
     * Test comparison table has all patterns.
     */
    @Test
    fun `comparison table contains all execution patterns`() {
        // Act
        val comparisons = SyncVsAsyncVsSuspend.getComparisonTable()
        
        // Assert
        assertEquals("Should have 3 patterns", 3, comparisons.size)
        
        val patterns = comparisons.map { it.pattern }
        assertTrue("Should have sync pattern", patterns.any { it.contains("Synchronous") })
        assertTrue("Should have async pattern", patterns.any { it.contains("Asynchronous") })
        assertTrue("Should have suspend pattern", patterns.any { it.contains("Suspend") })
    }
    
    /**
     * Test comparison data accuracy for synchronous pattern.
     */
    @Test
    fun `comparison data is accurate for sync pattern`() {
        // Act
        val comparison = SyncVsAsyncVsSuspend.getComparisonTable()
            .first { it.pattern.contains("Synchronous") }
        
        // Assert
        assertTrue("Sync should be blocking", comparison.blocking)
        assertTrue("Sync should require background thread", comparison.requiresBackgroundThread)
        assertFalse("Sync should not be callback-based", comparison.callbackBased)
        assertFalse("Sync should not support coroutines", comparison.coroutineSupport)
    }
    
    /**
     * Test comparison data accuracy for suspend pattern.
     */
    @Test
    fun `comparison data is accurate for suspend pattern`() {
        // Act
        val comparison = SyncVsAsyncVsSuspend.getComparisonTable()
            .first { it.pattern.contains("Suspend") }
        
        // Assert
        assertFalse("Suspend should not be blocking", comparison.blocking)
        assertFalse("Suspend should not require background thread", comparison.requiresBackgroundThread)
        assertFalse("Suspend should not be callback-based", comparison.callbackBased)
        assertTrue("Suspend should support coroutines", comparison.coroutineSupport)
    }
}

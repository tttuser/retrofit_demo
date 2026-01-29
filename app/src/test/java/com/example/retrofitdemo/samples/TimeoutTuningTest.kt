package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * TimeoutTuningTest: Tests for TimeoutTuning sample.
 * 
 * Test strategy:
 * 1. Verify default timeout values
 * 2. Test short timeout fails on slow response
 * 3. Test long timeout succeeds on slow response
 * 4. Verify custom timeout configuration
 * 5. Test timeout inspection helpers
 */
class TimeoutTuningTest {
    
    private lateinit var mockWebServer: MockWebServer
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that default timeouts are set to OkHttp defaults.
     */
    @Test
    fun `default timeouts match OkHttp defaults`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = TimeoutTuning.createWithDefaultTimeouts(baseUrl)
        
        // Act: Get the OkHttpClient from Retrofit
        val client = retrofit.callFactory() as OkHttpClient
        val timeouts = TimeoutTuning.getTimeouts(client)
        
        // Assert: Default values should be 10 seconds
        assertEquals("Default connect timeout should be 10s", 10L, timeouts["connect"])
        assertEquals("Default read timeout should be 10s", 10L, timeouts["read"])
        assertEquals("Default write timeout should be 10s", 10L, timeouts["write"])
    }
    
    /**
     * Test that short timeout fails on slow response.
     */
    @Test
    fun `short timeout fails on slow response`() {
        // Arrange: Mock response with 3 second delay
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"slow data"}""")
                .setBodyDelay(3, TimeUnit.SECONDS)
        )
        
        val baseUrl = mockWebServer.url("/").toString()
        
        // Create Retrofit with 1 second timeout (shorter than response delay)
        val retrofit = TimeoutTuning.createWithShortTimeouts(baseUrl, timeoutSeconds = 1)
        val service = retrofit.create(TimeoutTuning.TimeoutService::class.java)
        
        // Act & Assert: Should timeout
        try {
            runBlocking {
                service.getData()
            }
            fail("Should have thrown SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            // Expected - request timed out
            assertTrue("Should be timeout exception", e.message?.contains("timeout") == true)
        } catch (e: Exception) {
            // Could also be wrapped in another exception
            assertTrue(
                "Should be timeout related: ${e.message}",
                e.message?.contains("timeout") == true || 
                e.cause is SocketTimeoutException
            )
        }
    }
    
    /**
     * Test that long timeout succeeds on slow response.
     */
    @Test
    fun `long timeout succeeds on slow response`() = runBlocking {
        // Arrange: Mock response with 2 second delay
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"slow but successful"}""")
                .setBodyDelay(2, TimeUnit.SECONDS)
        )
        
        val baseUrl = mockWebServer.url("/").toString()
        
        // Create Retrofit with 10 second timeout (longer than response delay)
        val retrofit = TimeoutTuning.createWithLongTimeouts(baseUrl, timeoutSeconds = 10)
        val service = retrofit.create(TimeoutTuning.TimeoutService::class.java)
        
        // Act: Should succeed despite delay
        val response = service.getData()
        
        // Assert: Response should be received
        assertNotNull("Response should not be null", response)
        assertEquals("slow but successful", response.value)
    }
    
    /**
     * Test that custom timeout values are applied correctly.
     */
    @Test
    fun `custom timeouts are configured correctly`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = TimeoutTuning.createWithCustomTimeouts(
            baseUrl = baseUrl,
            connectSeconds = 5,
            readSeconds = 15,
            writeSeconds = 10
        )
        
        // Act: Get configured timeouts
        val client = retrofit.callFactory() as OkHttpClient
        val timeouts = TimeoutTuning.getTimeouts(client)
        
        // Assert: Custom values should be applied
        assertEquals("Connect timeout should be 5s", 5L, timeouts["connect"])
        assertEquals("Read timeout should be 15s", 15L, timeouts["read"])
        assertEquals("Write timeout should be 10s", 10L, timeouts["write"])
    }
    
    /**
     * Test that short timeout configuration works.
     */
    @Test
    fun `short timeout configuration is applied`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = TimeoutTuning.createWithShortTimeouts(baseUrl, timeoutSeconds = 3)
        
        // Act: Get configured timeouts
        val client = retrofit.callFactory() as OkHttpClient
        val timeouts = TimeoutTuning.getTimeouts(client)
        
        // Assert: All timeouts should be 3 seconds
        assertEquals("Connect timeout should be 3s", 3L, timeouts["connect"])
        assertEquals("Read timeout should be 3s", 3L, timeouts["read"])
        assertEquals("Write timeout should be 3s", 3L, timeouts["write"])
    }
    
    /**
     * Test that long timeout configuration works.
     */
    @Test
    fun `long timeout configuration is applied`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = TimeoutTuning.createWithLongTimeouts(baseUrl, timeoutSeconds = 60)
        
        // Act: Get configured timeouts
        val client = retrofit.callFactory() as OkHttpClient
        val timeouts = TimeoutTuning.getTimeouts(client)
        
        // Assert: All timeouts should be 60 seconds
        assertEquals("Connect timeout should be 60s", 60L, timeouts["connect"])
        assertEquals("Read timeout should be 60s", 60L, timeouts["read"])
        assertEquals("Write timeout should be 60s", 60L, timeouts["write"])
    }
    
    /**
     * Test fast response completes within short timeout.
     */
    @Test
    fun `fast response completes within short timeout`() = runBlocking {
        // Arrange: Mock fast response (no delay)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"fast data"}""")
        )
        
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = TimeoutTuning.createWithShortTimeouts(baseUrl, timeoutSeconds = 1)
        val service = retrofit.create(TimeoutTuning.TimeoutService::class.java)
        
        // Act: Should complete quickly
        val response = service.getData()
        
        // Assert: Response should be received
        assertNotNull("Response should not be null", response)
        assertEquals("fast data", response.value)
    }
    
    /**
     * Test that different Retrofit instances can have different timeouts.
     */
    @Test
    fun `different instances have independent timeout configs`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        
        // Act: Create instances with different timeouts
        val shortRetrofit = TimeoutTuning.createWithShortTimeouts(baseUrl, timeoutSeconds = 5)
        val longRetrofit = TimeoutTuning.createWithLongTimeouts(baseUrl, timeoutSeconds = 30)
        
        val shortClient = shortRetrofit.callFactory() as OkHttpClient
        val longClient = longRetrofit.callFactory() as OkHttpClient
        
        val shortTimeouts = TimeoutTuning.getTimeouts(shortClient)
        val longTimeouts = TimeoutTuning.getTimeouts(longClient)
        
        // Assert: Each instance should have its own configuration
        assertEquals("Short instance connect timeout", 5L, shortTimeouts["connect"])
        assertEquals("Long instance connect timeout", 30L, longTimeouts["connect"])
        
        assertNotEquals(
            "Timeout configs should be different",
            shortTimeouts["read"],
            longTimeouts["read"]
        )
    }
    
    /**
     * Test timeout inspection helper method.
     */
    @Test
    fun `getTimeouts helper returns correct values`() {
        // Arrange: Create client with known timeouts
        val client = OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(14, TimeUnit.SECONDS)
            .writeTimeout(21, TimeUnit.SECONDS)
            .build()
        
        // Act: Get timeouts
        val timeouts = TimeoutTuning.getTimeouts(client)
        
        // Assert: Values should match configuration
        assertEquals(7L, timeouts["connect"])
        assertEquals(14L, timeouts["read"])
        assertEquals(21L, timeouts["write"])
    }
}

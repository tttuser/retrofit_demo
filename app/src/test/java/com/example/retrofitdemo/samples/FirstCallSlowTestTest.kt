package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * FirstCallSlowTestTest: Tests for FirstCallSlowTest sample.
 * 
 * Test strategy:
 * 1. Test timing measurement utilities
 * 2. Verify multiple call execution
 * 3. Test timing analysis
 * 4. Validate warmup behavior
 */
class FirstCallSlowTestTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: FirstCallSlowTest.PingService
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val retrofit = FirstCallSlowTest.createRetrofit(mockWebServer.url("/").toString())
        service = retrofit.create(FirstCallSlowTest.PingService::class.java)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that measureTime captures execution duration.
     */
    @Test
    fun `measureTime captures execution duration`() = runBlocking {
        // Arrange: Mock response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"timestamp":1234567890,"server":"test"}""")
        )
        
        // Act: Measure time
        val result = FirstCallSlowTest.measureTime {
            service.ping()
        }
        
        // Assert: Should have timing information
        assertTrue("Duration should be non-negative", result.durationMs >= 0)
        assertTrue("Timestamp should be set", result.timestamp > 0)
    }
    
    /**
     * Test that multiple calls are executed.
     */
    @Test
    fun `measureMultipleCalls executes specified number of calls`() = runBlocking {
        // Arrange: Queue multiple responses
        repeat(5) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"timestamp":${System.currentTimeMillis()},"server":"test"}""")
            )
        }
        
        // Act: Execute multiple calls
        val results = FirstCallSlowTest.measureMultipleCalls(service, count = 5)
        
        // Assert: Should have 5 results
        assertEquals("Should have 5 results", 5, results.size)
        
        // Verify all have timing information
        results.forEach { result ->
            assertTrue("Duration should be non-negative", result.durationMs >= 0)
        }
    }
    
    /**
     * Test timing analysis with multiple results.
     */
    @Test
    fun `analyzeTimings calculates statistics correctly`() = runBlocking {
        // Arrange: Queue responses
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"timestamp":${System.currentTimeMillis()},"server":"test"}""")
            )
        }
        
        // Act: Execute and analyze
        val results = FirstCallSlowTest.measureMultipleCalls(service, count = 3)
        val analysis = FirstCallSlowTest.analyzeTimings(results)
        
        // Assert: Should have analysis data
        assertNotNull("Should have firstCallMs", analysis["firstCallMs"])
        assertNotNull("Should have avgSubsequentMs", analysis["avgSubsequentMs"])
        assertNotNull("Should have overhead", analysis["overhead"])
        assertNotNull("Should have overheadPercent", analysis["overheadPercent"])
        assertEquals("Should have correct call count", 3, analysis["callCount"])
        
        val firstCall = analysis["firstCallMs"] as Long
        assertTrue("First call should have duration", firstCall >= 0)
    }
    
    /**
     * Test analysis with only one call.
     */
    @Test
    fun `analyzeTimings handles single call`() = runBlocking {
        // Arrange: Queue one response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"timestamp":${System.currentTimeMillis()},"server":"test"}""")
        )
        
        // Act: Execute and analyze
        val results = FirstCallSlowTest.measureMultipleCalls(service, count = 1)
        val analysis = FirstCallSlowTest.analyzeTimings(results)
        
        // Assert: Should handle single call
        assertEquals("Should have 1 call", 1, analysis["callCount"])
        val avgSubsequent = analysis["avgSubsequentMs"] as Double
        assertEquals("Avg subsequent should be 0 for single call", 0.0, avgSubsequent, 0.1)
    }
    
    /**
     * Test warmup call.
     */
    @Test
    fun `warmup executes a call`() = runBlocking {
        // Arrange: Mock response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"timestamp":${System.currentTimeMillis()},"server":"test"}""")
        )
        
        // Act: Warmup
        val duration = FirstCallSlowTest.warmup(service)
        
        // Assert: Should have duration
        assertTrue("Warmup should have duration", duration >= 0)
        
        // Verify request was made
        val request = mockWebServer.takeRequest()
        assertEquals("Should call /ping", "/ping", request.path)
    }
    
    /**
     * Test that timing analysis handles empty results.
     */
    @Test
    fun `analyzeTimings handles empty results`() {
        // Act: Analyze empty list
        val analysis = FirstCallSlowTest.analyzeTimings(emptyList())
        
        // Assert: Should return empty map
        assertTrue("Should return empty map for empty results", analysis.isEmpty())
    }
    
    /**
     * Test that subsequent calls complete.
     */
    @Test
    fun `subsequent calls complete successfully`() = runBlocking {
        // Arrange: Queue responses
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"timestamp":${System.currentTimeMillis()},"server":"test-$it"}""")
            )
        }
        
        // Act: Execute calls
        val results = FirstCallSlowTest.measureMultipleCalls(service, count = 3)
        
        // Assert: All calls should complete
        assertEquals("Should have 3 results", 3, results.size)
        
        // Verify all requests were made
        repeat(3) {
            val request = mockWebServer.takeRequest()
            assertEquals("Should call /ping", "/ping", request.path)
        }
    }
    
    /**
     * Test measuring with actual delay.
     */
    @Test
    fun `measureTime captures minimum delay`() = runBlocking {
        // Arrange: Response with delay
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"timestamp":${System.currentTimeMillis()},"server":"test"}""")
                .setBodyDelay(50, java.util.concurrent.TimeUnit.MILLISECONDS)
        )
        
        // Act: Measure time
        val result = FirstCallSlowTest.measureTime {
            service.ping()
        }
        
        // Assert: Should capture at least the delay
        assertTrue("Duration should be at least 50ms", result.durationMs >= 50)
    }
}

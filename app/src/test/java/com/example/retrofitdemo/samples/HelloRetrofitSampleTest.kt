package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * HelloRetrofitSampleTest: Tests for HelloRetrofitSample.
 * 
 * Test strategy:
 * 1. Use MockWebServer to simulate API responses
 * 2. Validate request method, path, and query parameters
 * 3. Verify X-Request-Id header is present
 * 4. Validate response parsing
 */
class HelloRetrofitSampleTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var helloService: HelloRetrofitSample.HelloService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit and service
        val retrofit = HelloRetrofitSample.createRetrofit(mockWebServer.url("/").toString())
        helloService = HelloRetrofitSample.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that GET request uses correct method and path with path parameter.
     */
    @Test
    fun `getHello sends GET request with correct path parameter`() = runBlocking {
        // Arrange: Mock response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message":"Hello, World!","id":123}""")
        )
        
        // Act: Make request
        helloService.getHello(id = 123)
        
        // Assert: Verify request
        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertTrue("Path should contain /hello/123", request.path?.contains("/hello/123") == true)
    }
    
    /**
     * Test that GET request includes query parameter when provided.
     */
    @Test
    fun `getHello includes query parameter in URL`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message":"Hello, Alice!","id":456}""")
        )
        
        // Act: Make request with query parameter
        helloService.getHello(id = 456, name = "Alice")
        
        // Assert: Verify request
        val request = mockWebServer.takeRequest()
        val path = request.path ?: ""
        assertTrue("Path should contain /hello/456", path.contains("/hello/456"))
        assertTrue("Path should contain name=Alice", path.contains("name=Alice"))
    }
    
    /**
     * Test that X-Request-Id header is added to requests.
     * 
     * This verifies:
     * - SimpleRequestIdInterceptor adds the header
     * - Header value is a valid UUID format
     */
    @Test
    fun `getHello includes X-Request-Id header`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message":"Hello!","id":789}""")
        )
        
        // Act
        helloService.getHello(id = 789)
        
        // Assert: Verify X-Request-Id header
        val request = mockWebServer.takeRequest()
        val requestId = request.getHeader("X-Request-Id")
        assertNotNull("X-Request-Id header should be present", requestId)
        
        // Verify UUID format (8-4-4-4-12 hex digits)
        val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertTrue(
            "X-Request-Id should be a valid UUID",
            requestId!!.matches(uuidPattern)
        )
    }
    
    /**
     * Test that response is properly parsed.
     */
    @Test
    fun `getHello parses response correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message":"Test message","id":999}""")
        )
        
        // Act
        val response = helloService.getHello(id = 999, name = "Test")
        
        // Assert: Verify parsed response
        assertEquals("Test message", response.message)
        assertEquals(999, response.id)
    }
}

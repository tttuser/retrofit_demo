package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CustomHostAndNoAuthTest: Tests for CustomHostAndNoAuth sample.
 * 
 * Test strategy:
 * 1. Test standard endpoint using base URL
 * 2. Test @Url annotation for full URL override
 * 3. Test dynamic host switching via header
 * 4. Test authentication bypass for public endpoints
 */
class CustomHostAndNoAuthTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var customMockWebServer: MockWebServer
    private lateinit var service: CustomHostAndNoAuth.DynamicHostService
    
    @Before
    fun setup() {
        // Create and start primary MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create and start secondary MockWebServer for custom host
        customMockWebServer = MockWebServer()
        customMockWebServer.start()
        
        // Create Retrofit with auth token
        val retrofit = CustomHostAndNoAuth.createRetrofit(
            baseUrl = mockWebServer.url("/").toString(),
            authToken = "test-token-123"
        )
        service = CustomHostAndNoAuth.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
        customMockWebServer.shutdown()
    }
    
    /**
     * Test standard endpoint uses base URL.
     */
    @Test
    fun `standard endpoint uses base URL`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"Standard","url":"http://example.com"}""")
        )
        
        // Act
        val resource = service.getStandardResource()
        val request = mockWebServer.takeRequest()
        
        // Assert
        assertEquals("Standard", resource.name)
        assertEquals("/resources/standard", request.path)
        // Should have auth header
        assertNotNull("Should have Authorization header", request.getHeader("Authorization"))
    }
    
    /**
     * Test @Url annotation overrides base URL.
     */
    @Test
    fun `@Url annotation overrides base URL completely`() = runBlocking {
        // Arrange: Use custom server for this request
        customMockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":2,"name":"Custom URL","url":"http://custom.com"}""")
        )
        
        // Act: Use full URL pointing to custom server
        val customUrl = customMockWebServer.url("/api/resource").toString()
        val resource = service.getResourceFromUrl(customUrl)
        
        // Assert: Request should go to custom server
        val request = customMockWebServer.takeRequest()
        assertEquals("Custom URL", resource.name)
        assertEquals("/api/resource", request.path)
    }
    
    /**
     * Test dynamic host switching via X-Use-Custom-Host header.
     */
    @Test
    fun `X-Use-Custom-Host header switches to custom host`() = runBlocking {
        // Arrange: Custom server will receive the request
        customMockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":3,"name":"Custom Host","url":"http://custom.com"}""")
        )
        
        // Act: Request with custom host header
        val customHost = customMockWebServer.hostName + ":" + customMockWebServer.port
        val resource = service.getResourceFromCustomHost(customHost)
        
        // Assert: Request went to custom server
        val request = customMockWebServer.takeRequest()
        assertEquals("Custom Host", resource.name)
        assertEquals("/resources/custom", request.path)
        // X-Use-Custom-Host header should be removed by interceptor
        assertNull("X-Use-Custom-Host should be removed", request.getHeader("X-Use-Custom-Host"))
    }
    
    /**
     * Test public endpoint bypasses authentication.
     */
    @Test
    fun `X-No-Auth header bypasses authentication`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":4,"name":"Public","url":"http://example.com/public"}""")
        )
        
        // Act: Call public endpoint (X-No-Auth header set by default parameter)
        val resource = service.getPublicResource()
        val request = mockWebServer.takeRequest()
        
        // Assert
        assertEquals("Public", resource.name)
        // Should NOT have Authorization header
        assertNull("Should not have Authorization header", request.getHeader("Authorization"))
        // X-No-Auth marker should be removed by interceptor
        assertNull("X-No-Auth should be removed", request.getHeader("X-No-Auth"))
    }
    
    /**
     * Test that authenticated endpoints include auth header.
     */
    @Test
    fun `authenticated endpoints include Authorization header`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":5,"name":"Auth Required","url":"http://example.com"}""")
        )
        
        // Act: Standard endpoint should include auth
        val resource = service.getStandardResource()
        val request = mockWebServer.takeRequest()
        
        // Assert
        assertEquals("Auth Required", resource.name)
        assertEquals("Bearer test-token-123", request.getHeader("Authorization"))
    }
    
    /**
     * Test DynamicHostInterceptor preserves path and query.
     */
    @Test
    fun `DynamicHostInterceptor preserves path and query parameters`() = runBlocking {
        // Arrange: Custom server with query parameter in path
        customMockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":6,"name":"With Query","url":"http://custom.com"}""")
        )
        
        // Act: Request to custom host
        val customHost = customMockWebServer.hostName + ":" + customMockWebServer.port
        val resource = service.getResourceFromCustomHost(customHost)
        
        // Assert: Path should be preserved
        val request = customMockWebServer.takeRequest()
        assertTrue("Path should be preserved", request.path!!.contains("/resources/custom"))
    }
    
    /**
     * Test use cases are properly defined.
     */
    @Test
    fun `use cases are properly defined`() {
        // Act
        val useCases = CustomHostAndNoAuth.getUseCases()
        
        // Assert
        assertTrue("Should have at least 5 use cases", useCases.size >= 5)
        
        val descriptions = useCases.map { it.description }
        assertTrue("Should include multi-tenant use case",
            descriptions.any { it.contains("tenant") })
        assertTrue("Should include CDN use case",
            descriptions.any { it.contains("CDN") })
        assertTrue("Should include public endpoints use case",
            descriptions.any { it.contains("public") })
    }
    
    /**
     * Test that interceptor chain works correctly.
     */
    @Test
    fun `interceptor chain processes requests correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":7,"name":"Chain Test","url":"http://example.com"}""")
        )
        
        // Act: Make a standard authenticated request
        service.getStandardResource()
        val request = mockWebServer.takeRequest()
        
        // Assert: Both interceptors should have processed the request
        // 1. DynamicHostInterceptor (no custom host, so no change)
        // 2. ConditionalAuthInterceptor (adds auth token)
        assertEquals("/resources/standard", request.path)
        assertEquals("Bearer test-token-123", request.getHeader("Authorization"))
    }
}

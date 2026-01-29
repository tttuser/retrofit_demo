package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Non2xxBehaviorMatrixTest: Tests for Non2xxBehaviorMatrix sample.
 * 
 * Test strategy:
 * 1. Test 2xx success codes (200, 204)
 * 2. Test 4xx client errors (400, 401, 403, 404, 429)
 * 3. Test 5xx server errors (500, 503)
 * 4. Verify behavior matrix accuracy
 */
class Non2xxBehaviorMatrixTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: Non2xxBehaviorMatrix.StatusCodeService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit and service
        val retrofit = Non2xxBehaviorMatrix.createRetrofit(mockWebServer.url("/").toString())
        service = Non2xxBehaviorMatrix.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test 200 OK status code.
     */
    @Test
    fun `200 OK is successful with body`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"Success"}""")
        )
        
        // Act
        val response = service.get200().execute()
        
        // Assert
        assertTrue("200 should be successful", response.isSuccessful)
        assertEquals(200, response.code())
        assertNotNull("200 should have body", response.body())
        assertEquals(1, response.body()?.id)
    }
    
    /**
     * Test 204 No Content status code.
     */
    @Test
    fun `204 No Content is successful without body`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )
        
        // Act
        val response = service.get204().execute()
        
        // Assert
        assertTrue("204 should be successful", response.isSuccessful)
        assertEquals(204, response.code())
        // Body may be null for 204
    }
    
    /**
     * Test 400 Bad Request status code.
     */
    @Test
    fun `400 Bad Request is client error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"Invalid request"}""")
        )
        
        // Act
        val response = service.get400().execute()
        
        // Assert
        assertFalse("400 should not be successful", response.isSuccessful)
        assertEquals(400, response.code())
        assertNotNull("400 should have error body", response.errorBody())
    }
    
    /**
     * Test 401 Unauthorized status code.
     */
    @Test
    fun `401 Unauthorized is client error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"Authentication required"}""")
        )
        
        // Act
        val response = service.get401().execute()
        
        // Assert
        assertFalse("401 should not be successful", response.isSuccessful)
        assertEquals(401, response.code())
        assertNotNull("401 should have error body", response.errorBody())
    }
    
    /**
     * Test 403 Forbidden status code.
     */
    @Test
    fun `403 Forbidden is client error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error":"Access denied"}""")
        )
        
        // Act
        val response = service.get403().execute()
        
        // Assert
        assertFalse("403 should not be successful", response.isSuccessful)
        assertEquals(403, response.code())
    }
    
    /**
     * Test 404 Not Found status code.
     */
    @Test
    fun `404 Not Found is client error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Resource not found"}""")
        )
        
        // Act
        val response = service.get404().execute()
        
        // Assert
        assertFalse("404 should not be successful", response.isSuccessful)
        assertEquals(404, response.code())
        assertNotNull("404 should have error body", response.errorBody())
    }
    
    /**
     * Test 429 Too Many Requests status code.
     */
    @Test
    fun `429 Too Many Requests is client error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":"Rate limit exceeded"}""")
        )
        
        // Act
        val response = service.get429().execute()
        
        // Assert
        assertFalse("429 should not be successful", response.isSuccessful)
        assertEquals(429, response.code())
    }
    
    /**
     * Test 500 Internal Server Error status code.
     */
    @Test
    fun `500 Internal Server Error is server error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"Server error"}""")
        )
        
        // Act
        val response = service.get500().execute()
        
        // Assert
        assertFalse("500 should not be successful", response.isSuccessful)
        assertEquals(500, response.code())
        assertNotNull("500 should have error body", response.errorBody())
    }
    
    /**
     * Test 503 Service Unavailable status code.
     */
    @Test
    fun `503 Service Unavailable is server error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"error":"Service down"}""")
        )
        
        // Act
        val response = service.get503().execute()
        
        // Assert
        assertFalse("503 should not be successful", response.isSuccessful)
        assertEquals(503, response.code())
    }
    
    /**
     * Test suspend Response<T> for success.
     */
    @Test
    fun `suspend Response allows inspection of 200 status`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":2,"name":"Suspend Success"}""")
        )
        
        // Act
        val response = service.get200Suspend()
        
        // Assert
        assertTrue("200 should be successful", response.isSuccessful)
        assertEquals(200, response.code())
        assertNotNull("Should have body", response.body())
    }
    
    /**
     * Test suspend Response<T> for error.
     */
    @Test
    fun `suspend Response allows inspection of 404 status`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Not found"}""")
        )
        
        // Act
        val response = service.get404Suspend()
        
        // Assert
        assertFalse("404 should not be successful", response.isSuccessful)
        assertEquals(404, response.code())
    }
    
    /**
     * Test behavior matrix has all common status codes.
     */
    @Test
    fun `behavior matrix includes common status codes`() {
        // Act
        val matrix = Non2xxBehaviorMatrix.getBehaviorMatrix()
        
        // Assert
        assertTrue("Should have at least 10 status codes", matrix.size >= 10)
        
        val codes = matrix.map { it.code }
        assertTrue("Should include 200", codes.contains(200))
        assertTrue("Should include 404", codes.contains(404))
        assertTrue("Should include 500", codes.contains(500))
    }
    
    /**
     * Test status code categorization.
     */
    @Test
    fun `categorizeStatusCode correctly categorizes codes`() {
        // Act & Assert
        assertEquals("Success (2xx)", Non2xxBehaviorMatrix.categorizeStatusCode(200))
        assertEquals("Success (2xx)", Non2xxBehaviorMatrix.categorizeStatusCode(204))
        assertEquals("Redirection (3xx)", Non2xxBehaviorMatrix.categorizeStatusCode(301))
        assertEquals("Client Error (4xx)", Non2xxBehaviorMatrix.categorizeStatusCode(404))
        assertEquals("Server Error (5xx)", Non2xxBehaviorMatrix.categorizeStatusCode(500))
    }
    
    /**
     * Test isSuccessful helper.
     */
    @Test
    fun `isSuccessful correctly identifies success codes`() {
        // Act & Assert
        assertTrue("200 is successful", Non2xxBehaviorMatrix.isSuccessful(200))
        assertTrue("204 is successful", Non2xxBehaviorMatrix.isSuccessful(204))
        assertFalse("404 is not successful", Non2xxBehaviorMatrix.isSuccessful(404))
        assertFalse("500 is not successful", Non2xxBehaviorMatrix.isSuccessful(500))
    }
    
    /**
     * Test hasBody helper.
     */
    @Test
    fun `hasBody correctly identifies codes with bodies`() {
        // Act & Assert
        assertTrue("200 has body", Non2xxBehaviorMatrix.hasBody(200))
        assertFalse("204 has no body", Non2xxBehaviorMatrix.hasBody(204))
        assertTrue("404 has body", Non2xxBehaviorMatrix.hasBody(404))
        assertTrue("500 has body", Non2xxBehaviorMatrix.hasBody(500))
    }
}

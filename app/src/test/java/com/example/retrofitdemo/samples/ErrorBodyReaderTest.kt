package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ErrorBodyReaderTest: Tests for ErrorBodyReader sample.
 * 
 * Test strategy:
 * 1. Test successful responses (no error body)
 * 2. Test 404 with plain text error body
 * 3. Test 500 with JSON error body
 * 4. Verify error body can be parsed as structured data
 * 5. Validate error info extraction
 */
class ErrorBodyReaderTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: ErrorBodyReader.ErrorTestService
    private lateinit var moshi: Moshi
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val retrofit = ErrorBodyReader.createRetrofit(mockWebServer.url("/").toString())
        service = retrofit.create(ErrorBodyReader.ErrorTestService::class.java)
        
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test successful response has no error body.
     */
    @Test
    fun `successful response has no error body`() = runBlocking {
        // Arrange: Mock successful response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"John Doe"}""")
        )
        
        // Act: Make request
        val response = service.getUser(1)
        
        // Assert: Success case
        assertTrue("Response should be successful", response.isSuccessful)
        assertNotNull("Body should be present", response.body())
        assertNull("Error body should be null", response.errorBody())
        
        val user = response.body()!!
        assertEquals(1, user.id)
        assertEquals("John Doe", user.name)
    }
    
    /**
     * Test reading plain text error body from 404 response.
     */
    @Test
    fun `read plain text error body from 404`() = runBlocking {
        // Arrange: Mock 404 response with plain error
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("User not found")
        )
        
        // Act: Make request
        val response = service.getUser(999)
        
        // Assert: Error case
        assertFalse("Response should not be successful", response.isSuccessful)
        assertEquals("Status code should be 404", 404, response.code())
        assertNull("Body should be null", response.body())
        
        // Read error body
        val errorBody = ErrorBodyReader.readErrorBodyAsString(response)
        assertNotNull("Error body should be present", errorBody)
        assertEquals("User not found", errorBody)
    }
    
    /**
     * Test reading structured JSON error body from 500 response.
     */
    @Test
    fun `read structured JSON error body from 500`() = runBlocking {
        // Arrange: Mock 500 response with JSON error
        val errorJson = """{
            "error": "Internal Server Error",
            "message": "Database connection failed",
            "code": 500
        }"""
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody(errorJson)
        )
        
        // Act: Make request
        val response = service.getUser(1)
        
        // Assert: Error case
        assertFalse("Response should not be successful", response.isSuccessful)
        assertEquals("Status code should be 500", 500, response.code())
        
        // Read and parse error body
        val apiError = ErrorBodyReader.readErrorBodyAsObject(response, moshi)
        assertNotNull("Parsed error should be present", apiError)
        assertEquals("Internal Server Error", apiError!!.error)
        assertEquals("Database connection failed", apiError.message)
        assertEquals(500, apiError.code)
    }
    
    /**
     * Test reading error body as string for JSON errors.
     */
    @Test
    fun `read JSON error body as string`() = runBlocking {
        // Arrange: Mock 400 response with JSON error
        val errorJson = """{
            "error": "Bad Request",
            "message": "Invalid user ID",
            "code": 400
        }"""
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(errorJson)
        )
        
        // Act: Make request
        val response = service.getUser(-1)
        
        // Assert: Can read as string
        val errorBody = ErrorBodyReader.readErrorBodyAsString(response)
        assertNotNull("Error body should be present", errorBody)
        assertTrue("Error body should contain JSON", errorBody!!.contains("Bad Request"))
        assertTrue("Error body should contain message", errorBody.contains("Invalid user ID"))
    }
    
    /**
     * Test extracting complete error information.
     */
    @Test
    fun `extract complete error information`() = runBlocking {
        // Arrange: Mock 403 response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("Forbidden")
        )
        
        // Act: Make request
        val response = service.getUser(1)
        val errorInfo = ErrorBodyReader.extractErrorInfo(response)
        
        // Assert: All error info should be extracted
        assertEquals(false, errorInfo["isSuccessful"])
        assertEquals(403, errorInfo["code"])
        assertNotNull(errorInfo["message"]) // HTTP message like "Client Error"
        assertEquals("Forbidden", errorInfo["errorBody"])
        assertNull(errorInfo["body"]) // Body is null for errors
    }
    
    /**
     * Test that error body returns null for successful responses.
     */
    @Test
    fun `error body is null for successful responses`() = runBlocking {
        // Arrange: Mock successful response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":5,"name":"Jane Smith"}""")
        )
        
        // Act: Make request
        val response = service.getUser(5)
        
        // Assert: No error body for success
        val errorBody = ErrorBodyReader.readErrorBodyAsString(response)
        assertNull("Error body should be null for success", errorBody)
    }
    
    /**
     * Test parsing error body handles malformed JSON gracefully.
     */
    @Test
    fun `parsing malformed JSON error body returns null`() = runBlocking {
        // Arrange: Mock response with invalid JSON
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("This is not valid JSON {")
        )
        
        // Act: Make request
        val response = service.getUser(1)
        
        // Assert: Parsing should fail gracefully
        val apiError = ErrorBodyReader.readErrorBodyAsObject(response, moshi)
        assertNull("Should return null for malformed JSON", apiError)
    }
    
    /**
     * Test multiple error response codes.
     */
    @Test
    fun `handle various HTTP error codes`() = runBlocking {
        // Test different error codes
        val testCases = listOf(
            400 to "Bad Request",
            401 to "Unauthorized",
            403 to "Forbidden",
            404 to "Not Found",
            500 to "Internal Server Error",
            502 to "Bad Gateway",
            503 to "Service Unavailable"
        )
        
        testCases.forEach { (code, message) ->
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(code)
                    .setBody(message)
            )
            
            // Act
            val response = service.getUser(1)
            val errorBody = ErrorBodyReader.readErrorBodyAsString(response)
            
            // Assert
            assertFalse("Response should not be successful for $code", response.isSuccessful)
            assertEquals("Status code should be $code", code, response.code())
            assertEquals("Error body should match", message, errorBody)
        }
    }
}

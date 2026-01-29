package com.example.retrofitdemo.samples

import com.example.retrofitdemo.network.ApiResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * ApiResultDesignTest: Tests for ApiResultDesign sample.
 * 
 * Test strategy:
 * 1. Test manual wrapping of successful responses
 * 2. Test manual wrapping of error responses
 * 3. Test ApiResult handling patterns
 * 4. Test utility functions (fold, map, getOrDefault, etc.)
 */
class ApiResultDesignTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: ApiResultDesign.UserService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit and service
        val retrofit = ApiResultDesign.createRetrofit(mockWebServer.url("/").toString())
        service = ApiResultDesign.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test wrapping a successful response in ApiResult.
     */
    @Test
    fun `wrapInApiResult wraps successful response`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"username":"alice","email":"alice@example.com"}""")
        )
        
        // Act
        val call = service.getUser(1)
        val result = ApiResultDesign.wrapInApiResult(call)
        
        // Assert
        assertTrue("Result should be Success", result is ApiResult.Success)
        val user = (result as ApiResult.Success).data
        assertEquals(1, user.id)
        assertEquals("alice", user.username)
        assertEquals("alice@example.com", user.email)
    }
    
    /**
     * Test wrapping an HTTP error in ApiResult.
     */
    @Test
    fun `wrapInApiResult wraps HTTP error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"User not found"}""")
        )
        
        // Act
        val call = service.getUser(999)
        val result = ApiResultDesign.wrapInApiResult(call)
        
        // Assert
        assertTrue("Result should be Error", result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertTrue("Exception message should mention HTTP 404", 
            error.exception.message?.contains("404") == true)
        assertTrue("Error body should be present",
            error.errorBody?.contains("User not found") == true)
    }
    
    /**
     * Test wrapping a network error in ApiResult.
     */
    @Test
    fun `wrapInApiResult wraps network error`() {
        // Arrange: Shutdown server to cause connection error
        mockWebServer.shutdown()
        
        // Act
        val call = service.getUser(1)
        val result = ApiResultDesign.wrapInApiResult(call)
        
        // Assert
        assertTrue("Result should be Error", result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertTrue("Exception should be IOException or ConnectException",
            error.exception is IOException)
    }
    
    /**
     * Test handling ApiResult.Success with when expression.
     */
    @Test
    fun `handleResult returns success message for Success`() {
        // Arrange
        val user = ApiResultDesign.User(1, "bob", "bob@example.com")
        val result = ApiResult.Success(user)
        
        // Act
        val message = ApiResultDesign.handleResult(result)
        
        // Assert
        assertTrue("Message should contain username", message.contains("bob"))
        assertTrue("Message should contain email", message.contains("bob@example.com"))
        assertTrue("Message should indicate success", message.contains("Success"))
    }
    
    /**
     * Test handling ApiResult.Error with when expression.
     */
    @Test
    fun `handleResult returns error message for Error`() {
        // Arrange
        val result = ApiResult.Error(
            exception = Exception("Network failure"),
            errorBody = """{"error":"Connection refused"}"""
        )
        
        // Act
        val message = ApiResultDesign.handleResult(result)
        
        // Assert
        assertTrue("Message should contain error", message.contains("Error"))
        assertTrue("Message should contain exception message", message.contains("Network failure"))
        assertTrue("Message should contain error body", message.contains("Connection refused"))
    }
    
    /**
     * Test fold function with Success.
     */
    @Test
    fun `fold handles Success case`() {
        // Arrange
        val user = ApiResultDesign.User(2, "charlie", "charlie@example.com")
        val result = ApiResult.Success(user)
        
        // Act
        val output = ApiResultDesign.fold(
            result,
            onSuccess = { "User: ${it.username}" },
            onError = { _, _ -> "Error occurred" }
        )
        
        // Assert
        assertEquals("User: charlie", output)
    }
    
    /**
     * Test fold function with Error.
     */
    @Test
    fun `fold handles Error case`() {
        // Arrange
        val result = ApiResult.Error(
            exception = Exception("Test error"),
            errorBody = null
        )
        
        // Act
        val output = ApiResultDesign.fold(
            result,
            onSuccess = { "Success" },
            onError = { ex, _ -> "Error: ${ex.message}" }
        )
        
        // Assert
        assertEquals("Error: Test error", output)
    }
    
    /**
     * Test map function with Success.
     */
    @Test
    fun `map transforms Success data`() {
        // Arrange
        val user = ApiResultDesign.User(3, "dave", "dave@example.com")
        val result = ApiResult.Success(user)
        
        // Act: Transform User to username string
        val mapped = ApiResultDesign.map(result) { it.username }
        
        // Assert
        assertTrue("Mapped result should be Success", mapped is ApiResult.Success)
        assertEquals("dave", (mapped as ApiResult.Success).data)
    }
    
    /**
     * Test map function preserves Error.
     */
    @Test
    fun `map preserves Error`() {
        // Arrange
        val result: ApiResult<ApiResultDesign.User> = ApiResult.Error(
            exception = Exception("Test error"),
            errorBody = null
        )
        
        // Act: Try to transform (should preserve error)
        val mapped = ApiResultDesign.map(result) { it.username }
        
        // Assert
        assertTrue("Mapped result should still be Error", mapped is ApiResult.Error)
        assertEquals("Test error", (mapped as ApiResult.Error).exception.message)
    }
    
    /**
     * Test getOrDefault with Success.
     */
    @Test
    fun `getOrDefault returns data for Success`() {
        // Arrange
        val user = ApiResultDesign.User(4, "eve", "eve@example.com")
        val result = ApiResult.Success(user)
        val defaultUser = ApiResultDesign.User(0, "default", "default@example.com")
        
        // Act
        val output = ApiResultDesign.getOrDefault(result, defaultUser)
        
        // Assert
        assertEquals(user, output)
        assertEquals("eve", output.username)
    }
    
    /**
     * Test getOrDefault with Error.
     */
    @Test
    fun `getOrDefault returns default for Error`() {
        // Arrange
        val result: ApiResult<ApiResultDesign.User> = ApiResult.Error(
            exception = Exception("Test error"),
            errorBody = null
        )
        val defaultUser = ApiResultDesign.User(0, "default", "default@example.com")
        
        // Act
        val output = ApiResultDesign.getOrDefault(result, defaultUser)
        
        // Assert
        assertEquals(defaultUser, output)
        assertEquals("default", output.username)
    }
    
    /**
     * Test getOrNull with Success.
     */
    @Test
    fun `getOrNull returns data for Success`() {
        // Arrange
        val user = ApiResultDesign.User(5, "frank", "frank@example.com")
        val result = ApiResult.Success(user)
        
        // Act
        val output = ApiResultDesign.getOrNull(result)
        
        // Assert
        assertNotNull("Output should not be null", output)
        assertEquals("frank", output?.username)
    }
    
    /**
     * Test getOrNull with Error.
     */
    @Test
    fun `getOrNull returns null for Error`() {
        // Arrange
        val result: ApiResult<ApiResultDesign.User> = ApiResult.Error(
            exception = Exception("Test error"),
            errorBody = null
        )
        
        // Act
        val output = ApiResultDesign.getOrNull(result)
        
        // Assert
        assertNull("Output should be null", output)
    }
}

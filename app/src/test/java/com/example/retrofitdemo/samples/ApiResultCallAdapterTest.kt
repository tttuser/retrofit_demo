package com.example.retrofitdemo.samples

import com.example.retrofitdemo.network.ApiResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ApiResultCallAdapterTest: Tests for ApiResultCallAdapter sample.
 * 
 * Test strategy:
 * 1. Test automatic wrapping of successful responses
 * 2. Test automatic wrapping of HTTP errors
 * 3. Test automatic wrapping of network errors
 * 4. Test that benefits are accurately described
 */
class ApiResultCallAdapterTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: ApiResultCallAdapter.PostService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit with ApiResultCallAdapterFactory
        val retrofit = ApiResultCallAdapter.createRetrofit(mockWebServer.url("/").toString())
        service = ApiResultCallAdapter.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that CallAdapter automatically wraps successful response.
     */
    @Test
    fun `CallAdapter wraps successful response in ApiResult Success`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"title":"Test Post","body":"Test content"}""")
        )
        
        // Act
        val call = service.getPost(1)
        val response = call.execute()
        val result = response.body()!!
        
        // Assert
        assertTrue("Result should be Success", result is ApiResult.Success)
        val post = (result as ApiResult.Success).data
        assertEquals(1, post.id)
        assertEquals("Test Post", post.title)
        assertEquals("Test content", post.body)
    }
    
    /**
     * Test that CallAdapter automatically wraps HTTP error.
     */
    @Test
    fun `CallAdapter wraps HTTP error in ApiResult Error`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Post not found"}""")
        )
        
        // Act
        val call = service.getPost(999)
        val response = call.execute()
        val result = response.body()!!
        
        // Assert
        assertTrue("Result should be Error", result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertTrue("Exception message should mention HTTP 404",
            error.exception.message?.contains("404") == true)
        assertTrue("Error body should be present",
            error.errorBody?.contains("Post not found") == true)
    }
    
    /**
     * Test that CallAdapter automatically wraps network error.
     */
    @Test
    fun `CallAdapter wraps network error in ApiResult Error`() {
        // Arrange: Shutdown server to cause connection error
        mockWebServer.shutdown()
        
        // Act
        val call = service.getPost(1)
        val response = call.execute()
        val result = response.body()!!
        
        // Assert
        assertTrue("Result should be Error", result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertNotNull("Exception should be present", error.exception)
    }
    
    /**
     * Test wrapping list responses.
     */
    @Test
    fun `CallAdapter wraps list responses correctly`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[
                    {"id":1,"title":"Post 1","body":"Content 1"},
                    {"id":2,"title":"Post 2","body":"Content 2"}
                ]""")
        )
        
        // Act
        val call = service.getPosts()
        val response = call.execute()
        val result = response.body()!!
        
        // Assert
        assertTrue("Result should be Success", result is ApiResult.Success)
        val posts = (result as ApiResult.Success).data
        assertEquals(2, posts.size)
        assertEquals("Post 1", posts[0].title)
        assertEquals("Post 2", posts[1].title)
    }
    
    /**
     * Test fetchPost helper function with success.
     */
    @Test
    fun `fetchPost returns success message for successful response`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":2,"title":"Example Post","body":"Example content"}""")
        )
        
        // Act
        val message = ApiResultCallAdapter.fetchPost(service, 2)
        
        // Assert
        assertTrue("Message should indicate success", message.contains("Success"))
        assertTrue("Message should contain title", message.contains("Example Post"))
        assertTrue("Message should contain ID", message.contains("2"))
    }
    
    /**
     * Test fetchPost helper function with error.
     */
    @Test
    fun `fetchPost returns error message for error response`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"Server error"}""")
        )
        
        // Act
        val message = ApiResultCallAdapter.fetchPost(service, 999)
        
        // Assert
        assertTrue("Message should indicate error", message.contains("Error"))
        assertTrue("Message should mention HTTP 500", message.contains("500"))
    }
    
    /**
     * Test that response is always present (never null).
     */
    @Test
    fun `response body is always present with CallAdapter`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":3,"title":"Post","body":"Content"}""")
        )
        
        // Act
        val call = service.getPost(3)
        val response = call.execute()
        
        // Assert
        assertNotNull("Response should not be null", response)
        assertNotNull("Response body should not be null", response.body())
        assertTrue("Response body should be ApiResult", response.body() is ApiResult<*>)
    }
    
    /**
     * Test that HTTP response is always successful (200) for CallAdapter.
     * Even errors are wrapped in Success response with Error body.
     */
    @Test
    fun `HTTP response is always successful with CallAdapter`() {
        // Arrange: Mock a 404 error
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Not found"}""")
        )
        
        // Act
        val call = service.getPost(999)
        val response = call.execute()
        
        // Assert
        assertTrue("HTTP response should be successful", response.isSuccessful)
        assertEquals("HTTP code should be 200", 200, response.code())
        
        // But the ApiResult contains the error
        val result = response.body()!!
        assertTrue("ApiResult should be Error", result is ApiResult.Error)
    }
    
    /**
     * Test benefits description.
     */
    @Test
    fun `benefits are accurately described`() {
        // Act
        val benefits = ApiResultCallAdapter.getBenefits()
        
        // Assert
        assertTrue("Should eliminate boilerplate", benefits.eliminatesBoilerplate)
        assertTrue("Should be type-safe", benefits.typeSafe)
        assertTrue("Should provide consistent error handling", benefits.consistentErrorHandling)
        assertTrue("Should require exhaustive handling", benefits.exhaustiveHandling)
        assertTrue("Should wrap errors uniformly", benefits.uniformErrorWrapping)
    }
}

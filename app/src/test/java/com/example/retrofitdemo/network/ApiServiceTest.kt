package com.example.retrofitdemo.network

import com.example.retrofitdemo.model.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ApiServiceTest: Unit tests for ApiService using MockWebServer.
 * 
 * Test strategy:
 * 1. Use MockWebServer to simulate API responses without hitting real servers
 * 2. Validate request shape (path, query parameters, headers)
 * 3. Validate response parsing and error handling
 * 4. Test custom CallAdapter behavior
 * 
 * Source reading notes:
 * - MockWebServer runs a local HTTP server for testing
 * - takeRequest() captures the actual request sent by the client
 * - We can verify request details (method, path, headers, body)
 * - We can mock different response scenarios (success, error, network failure)
 */
class ApiServiceTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create OkHttpClient with RequestIdInterceptor
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(RequestIdInterceptor())
            .connectTimeout(1, TimeUnit.SECONDS)
            .build()
        
        // Create Moshi for JSON parsing
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        // Create Retrofit pointing to MockWebServer
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(ApiResultCallAdapterFactory())
            .build()
        
        apiService = retrofit.create(ApiService::class.java)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test 1: Validate request shape - path and query parameters
     * 
     * Verifies:
     * - Path parameter is correctly substituted in URL
     * - Query parameter is correctly appended to URL
     * - Request method is GET
     */
    @Test
    fun `getUser sends correct path and query parameters`() = runBlocking {
        // Arrange: Mock successful response
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""{"id":1,"name":"John Doe","email":"john@example.com"}""")
        mockWebServer.enqueue(mockResponse)
        
        // Act: Make the API call
        apiService.getUser(userId = 1, includeEmail = true)
        
        // Assert: Verify request shape
        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/users/1?includeEmail=true", request.path)
    }
    
    /**
     * Test 2: Validate X-Request-Id header is added by RequestIdInterceptor
     * 
     * Verifies:
     * - RequestIdInterceptor adds X-Request-Id header
     * - Header value is a valid UUID format
     */
    @Test
    fun `requests include X-Request-Id header`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"John Doe","email":"john@example.com"}""")
        )
        
        // Act
        apiService.getUser(userId = 1)
        
        // Assert
        val request = mockWebServer.takeRequest()
        val requestId = request.getHeader("X-Request-Id")
        assertNotNull("X-Request-Id header should be present", requestId)
        
        // Verify it's a valid UUID format (8-4-4-4-12 hex digits)
        val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertTrue(
            "X-Request-Id should be a valid UUID",
            requestId!!.matches(uuidPattern)
        )
    }
    
    /**
     * Test 3: Validate non-2xx behavior with Response<T>
     * 
     * Verifies:
     * - Response<T> correctly handles non-2xx status codes
     * - Error body is accessible via response.errorBody()
     * - response.isSuccessful returns false for errors
     */
    @Test
    fun `getUserWithResponse handles non-2xx responses correctly`() = runBlocking {
        // Arrange: Mock 404 error response
        val errorBody = """{"error":"User not found"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(errorBody)
        )
        
        // Act
        val response = apiService.getUserWithResponse(userId = 999)
        
        // Assert
        assertFalse("Response should not be successful", response.isSuccessful)
        assertEquals("Status code should be 404", 404, response.code())
        assertNull("Response body should be null for errors", response.body())
        assertEquals(
            "Error body should contain error message",
            errorBody,
            response.errorBody()?.string()
        )
    }
    
    /**
     * Test 4: Validate successful Response<T> behavior
     * 
     * Verifies:
     * - Response<T> correctly handles 2xx status codes
     * - Response body is properly parsed
     * - response.isSuccessful returns true
     */
    @Test
    fun `getUserWithResponse handles 2xx responses correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"John Doe","email":"john@example.com"}""")
        )
        
        // Act
        val response = apiService.getUserWithResponse(userId = 1)
        
        // Assert
        assertTrue("Response should be successful", response.isSuccessful)
        assertEquals("Status code should be 200", 200, response.code())
        assertNotNull("Response body should not be null", response.body())
        assertEquals("User ID should be 1", 1, response.body()?.id)
        assertEquals("User name should be John Doe", "John Doe", response.body()?.name)
    }
    
    /**
     * Test 5: Validate Call<T> can be executed
     * 
     * Verifies:
     * - Call<T> can be executed synchronously
     * - Response is properly parsed
     */
    @Test
    fun `getPosts returns Call that can be executed`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":1,"userId":1,"title":"Test Post","body":"Test body"}]""")
        )
        
        // Act
        val call = apiService.getPosts()
        val response = call.execute()
        
        // Assert
        assertTrue("Response should be successful", response.isSuccessful)
        val posts = response.body()
        assertNotNull("Posts should not be null", posts)
        assertEquals("Should have 1 post", 1, posts?.size)
        assertEquals("Post ID should be 1", 1, posts?.get(0)?.id)
    }
    
    /**
     * Test 6: Validate form-encoded request body
     * 
     * Verifies:
     * - @FormUrlEncoded annotation produces correct Content-Type
     * - @Field parameters are correctly encoded in request body
     */
    @Test
    fun `login sends form-encoded body`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"abc123","userId":1}""")
        )
        
        // Act
        val call = apiService.login(username = "testuser", password = "testpass")
        call.execute()
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "application/x-www-form-urlencoded",
            request.getHeader("Content-Type")
        )
        val body = request.body.readUtf8()
        assertTrue("Body should contain username", body.contains("username=testuser"))
        assertTrue("Body should contain password", body.contains("password=testpass"))
    }
    
    /**
     * Test 7: Validate custom CallAdapter wraps success in ApiResult.Success
     * 
     * Verifies:
     * - ApiResultCallAdapterFactory correctly wraps successful responses
     * - Result is ApiResult.Success with correct data
     */
    @Test
    fun `getUserAsApiResult wraps success response in ApiResult_Success`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"John Doe","email":"john@example.com"}""")
        )
        
        // Act
        val call = apiService.getUserAsApiResult(userId = 1)
        val response = call.execute()
        
        // Assert
        assertTrue("Response should be successful", response.isSuccessful)
        val result = response.body()
        assertNotNull("Result should not be null", result)
        assertTrue("Result should be Success", result is ApiResult.Success)
        
        val successResult = result as ApiResult.Success
        assertEquals("User ID should be 1", 1, successResult.data.id)
        assertEquals("User name should be John Doe", "John Doe", successResult.data.name)
    }
    
    /**
     * Test 8: Validate custom CallAdapter wraps errors in ApiResult.Error
     * 
     * Verifies:
     * - ApiResultCallAdapterFactory correctly wraps error responses
     * - Result is ApiResult.Error with error details
     * - Error body is captured
     */
    @Test
    fun `getUserAsApiResult wraps error response in ApiResult_Error`() {
        // Arrange
        val errorBody = """{"error":"User not found"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(errorBody)
        )
        
        // Act
        val call = apiService.getUserAsApiResult(userId = 999)
        val response = call.execute()
        
        // Assert
        assertTrue("Response should be successful (ApiResult wraps errors)", response.isSuccessful)
        val result = response.body()
        assertNotNull("Result should not be null", result)
        assertTrue("Result should be Error", result is ApiResult.Error)
        
        val errorResult = result as ApiResult.Error
        assertTrue(
            "Exception should be HttpException",
            errorResult.exception is ApiResultCallAdapterFactory.HttpException
        )
        assertEquals("Error body should be captured", errorBody, errorResult.errorBody)
        
        val httpException = errorResult.exception as ApiResultCallAdapterFactory.HttpException
        assertEquals("HTTP status code should be 404", 404, httpException.code)
    }
    
    /**
     * Test 9: Validate custom CallAdapter wraps network failures in ApiResult.Error
     * 
     * Verifies:
     * - ApiResultCallAdapterFactory handles network failures gracefully
     * - Network errors are wrapped in ApiResult.Error
     */
    @Test
    fun `getUserAsApiResult wraps network failure in ApiResult_Error`() {
        // Arrange: Shutdown server to simulate network failure
        mockWebServer.shutdown()
        
        // Act
        val call = apiService.getUserAsApiResult(userId = 1)
        val response = call.execute()
        
        // Assert
        assertTrue("Response should be successful (ApiResult wraps errors)", response.isSuccessful)
        val result = response.body()
        assertNotNull("Result should not be null", result)
        assertTrue("Result should be Error", result is ApiResult.Error)
        
        val errorResult = result as ApiResult.Error
        assertNotNull("Exception should not be null", errorResult.exception)
    }
}

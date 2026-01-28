package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * ReturnTypesComparisonSampleTest: Tests demonstrating return type differences.
 * 
 * Test strategy:
 * 1. Test Call<T> execution for both success and error
 * 2. Test suspend T throws on error
 * 3. Test suspend Response<T> allows error inspection
 * 4. Compare behavior differences for non-2xx responses
 */
class ReturnTypesComparisonSampleTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: ReturnTypesComparisonSample.ComparisonService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit and service
        val retrofit = ReturnTypesComparisonSample.createRetrofit(mockWebServer.url("/").toString())
        service = ReturnTypesComparisonSample.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test Call<T> returns successful response.
     */
    @Test
    fun `Call returns successful response`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"Item 1"}""")
        )
        
        // Act
        val call = service.getItemAsCall(1)
        val response = call.execute()
        
        // Assert
        assertTrue("Response should be successful", response.isSuccessful)
        assertNotNull("Body should not be null", response.body())
        assertEquals(1, response.body()?.id)
        assertEquals("Item 1", response.body()?.name)
    }
    
    /**
     * Test Call<T> allows inspection of non-2xx response.
     */
    @Test
    fun `Call allows inspection of 404 response`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Not found"}""")
        )
        
        // Act
        val call = service.getItemAsCall(999)
        val response = call.execute()
        
        // Assert: Call does not throw, allows inspection
        assertFalse("Response should not be successful", response.isSuccessful)
        assertEquals(404, response.code())
        assertNull("Body should be null for error", response.body())
        val errorBody = response.errorBody()?.string()
        assertTrue("Error body should contain error message", errorBody?.contains("Not found") == true)
    }
    
    /**
     * Test suspend T returns successful response.
     */
    @Test
    fun `suspend T returns direct result on success`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":2,"name":"Item 2"}""")
        )
        
        // Act
        val item = service.getItemDirect(2)
        
        // Assert
        assertEquals(2, item.id)
        assertEquals("Item 2", item.name)
    }
    
    /**
     * Test suspend T throws HttpException on non-2xx response.
     * This is the key difference from Call<T> and Response<T>.
     */
    @Test
    fun `suspend T throws HttpException on 404`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Not found"}""")
        )
        
        // Act & Assert: Expect HttpException to be thrown
        try {
            service.getItemDirect(999)
            fail("Should have thrown HttpException")
        } catch (e: HttpException) {
            // Expected behavior
            assertEquals(404, e.code())
            val errorBody = e.response()?.errorBody()?.string()
            assertTrue("Error body should be accessible", errorBody?.contains("Not found") == true)
        }
    }
    
    /**
     * Test suspend Response<T> returns successful response.
     */
    @Test
    fun `suspend Response returns full response on success`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":3,"name":"Item 3"}""")
        )
        
        // Act
        val response = service.getItemAsResponse(3)
        
        // Assert
        assertTrue("Response should be successful", response.isSuccessful)
        assertEquals(200, response.code())
        assertNotNull("Body should not be null", response.body())
        assertEquals(3, response.body()?.id)
        assertEquals("Item 3", response.body()?.name)
    }
    
    /**
     * Test suspend Response<T> allows inspection of non-2xx without throwing.
     * This is similar to Call<T> but with coroutine support.
     */
    @Test
    fun `suspend Response allows inspection of 500 error`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"Internal server error"}""")
        )
        
        // Act
        val response = service.getItemAsResponse(999)
        
        // Assert: Does not throw, allows inspection
        assertFalse("Response should not be successful", response.isSuccessful)
        assertEquals(500, response.code())
        assertNull("Body should be null for error", response.body())
        val errorBody = response.errorBody()?.string()
        assertTrue("Error body should contain error", errorBody?.contains("Internal server error") == true)
    }
    
    /**
     * Test comparing all three return types for the same error scenario.
     * This demonstrates the key behavioral differences.
     */
    @Test
    fun `compare all return types for 404 error`() = runBlocking {
        // Arrange: Enqueue 3 identical 404 responses
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("""{"error":"Not found"}""")
            )
        }
        
        // Test 1: Call<T> - Allows inspection
        val callResponse = service.getItemAsCall(999).execute()
        assertFalse("Call: should not be successful", callResponse.isSuccessful)
        assertEquals("Call: code should be 404", 404, callResponse.code())
        
        // Test 2: suspend T - Throws exception
        var exceptionThrown = false
        try {
            service.getItemDirect(999)
        } catch (e: HttpException) {
            exceptionThrown = true
            assertEquals("Direct: exception code should be 404", 404, e.code())
        }
        assertTrue("Direct: should throw exception", exceptionThrown)
        
        // Test 3: suspend Response<T> - Allows inspection
        val suspendResponse = service.getItemAsResponse(999)
        assertFalse("Response: should not be successful", suspendResponse.isSuccessful)
        assertEquals("Response: code should be 404", 404, suspendResponse.code())
    }
}

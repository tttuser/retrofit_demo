package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CallAdapterDetectTest: Tests for CallAdapterDetect sample.
 * 
 * Test strategy:
 * 1. Verify that LoggingCallAdapterFactory captures type queries
 * 2. Test that different return types trigger CallAdapter selection
 * 3. Validate that the correct adapters are used for each return type
 */
class CallAdapterDetectTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var loggingFactory: CallAdapterDetect.LoggingCallAdapterFactory
    private lateinit var service: CallAdapterDetect.DetectionService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create logging factory to track CallAdapter queries
        loggingFactory = CallAdapterDetect.LoggingCallAdapterFactory()
        
        // Create Retrofit and service
        val retrofit = CallAdapterDetect.createRetrofit(
            mockWebServer.url("/").toString(),
            loggingFactory
        )
        service = CallAdapterDetect.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that creating Call<T> return type queries CallAdapter factories.
     */
    @Test
    fun `Call return type queries CallAdapter factory`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"Item 1"}""")
        )
        
        // Act: Create a Call (this triggers CallAdapter selection)
        val call = service.getItemAsCall()
        
        // Assert: Verify the factory was queried with Call<Item> type
        val queriedTypes = loggingFactory.getQueriedTypesDescription()
        assertTrue(
            "Factory should be queried for Call<Item>",
            queriedTypes.any { it.contains("retrofit2.Call") }
        )
    }
    
    /**
     * Test that suspend function queries CallAdapter factory.
     */
    @Test
    fun `suspend function queries CallAdapter factory`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":2,"name":"Item 2"}""")
        )
        
        // Clear previous queries
        loggingFactory.queriedTypes.clear()
        
        // Act: Call suspend function
        service.getItemSuspend()
        
        // Assert: Verify the factory was queried
        // Suspend functions are transformed by Retrofit to use Continuation
        val queriedTypes = loggingFactory.getQueriedTypesDescription()
        assertTrue(
            "Factory should be queried for suspend function",
            queriedTypes.isNotEmpty()
        )
    }
    
    /**
     * Test that suspend Response<T> queries CallAdapter factory.
     */
    @Test
    fun `suspend Response return type queries CallAdapter factory`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":3,"name":"Item 3"}""")
        )
        
        // Clear previous queries
        loggingFactory.queriedTypes.clear()
        
        // Act: Call suspend Response function
        val response = service.getItemSuspendResponse()
        
        // Assert: Verify the factory was queried and response is successful
        val queriedTypes = loggingFactory.getQueriedTypesDescription()
        assertTrue(
            "Factory should be queried for suspend Response",
            queriedTypes.isNotEmpty()
        )
        assertTrue("Response should be successful", response.isSuccessful)
    }
    
    /**
     * Test Call adapter helper method.
     */
    @Test
    fun `usesDefaultCallAdapter detects Call types`() {
        // Arrange: Get the Call method return type
        val method = CallAdapterDetect.DetectionService::class.java
            .getMethod("getItemAsCall")
        val returnType = method.genericReturnType
        
        // Act & Assert
        assertTrue(
            "Call<T> should use default CallAdapter",
            CallAdapterDetect.usesDefaultCallAdapter(returnType)
        )
    }
    
    /**
     * Test that all API methods are properly defined.
     */
    @Test
    fun `service interface has all expected methods`() {
        val methods = CallAdapterDetect.DetectionService::class.java.methods
        val methodNames = methods.map { it.name }
        
        assertTrue("Should have getItemAsCall", methodNames.contains("getItemAsCall"))
        assertTrue("Should have getItemSuspend", methodNames.contains("getItemSuspend"))
        assertTrue("Should have getItemSuspendResponse", methodNames.contains("getItemSuspendResponse"))
    }
    
    /**
     * Test that Call<T> executes successfully.
     */
    @Test
    fun `Call execution works correctly`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":10,"name":"Test Item"}""")
        )
        
        // Act
        val call = service.getItemAsCall()
        val response = call.execute()
        
        // Assert
        assertTrue("Response should be successful", response.isSuccessful)
        assertEquals(10, response.body()?.id)
        assertEquals("Test Item", response.body()?.name)
    }
}

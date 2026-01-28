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
 * JsonBodyPostSampleTest: Tests for JsonBodyPostSample.
 * 
 * Test strategy:
 * 1. Verify POST method is used
 * 2. Verify Content-Type is application/json
 * 3. Verify request body contains expected JSON
 * 4. Validate JSON serialization of request
 * 5. Validate JSON deserialization of response
 */
class JsonBodyPostSampleTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var itemService: JsonBodyPostSample.ItemService
    private lateinit var moshi: Moshi
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit and service
        val retrofit = JsonBodyPostSample.createRetrofit(mockWebServer.url("/").toString())
        itemService = JsonBodyPostSample.createService(retrofit)
        
        // Create Moshi for JSON validation
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that POST request uses correct method.
     */
    @Test
    fun `createItem sends POST request`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"id":1,"name":"Test","description":"Desc","price":10.0,"tags":[],"createdAt":"2024-01-01T00:00:00Z"}""")
        )
        
        val request = JsonBodyPostSample.CreateItemRequest(
            name = "Test",
            description = "Desc",
            price = 10.0,
            tags = emptyList()
        )
        
        // Act
        itemService.createItem(request)
        
        // Assert: Verify request method
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
    }
    
    /**
     * Test that POST request has correct Content-Type header.
     */
    @Test
    fun `createItem sets Content-Type to application json`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"id":2,"name":"Widget","description":"Test widget","price":25.99,"tags":["tag1"],"createdAt":"2024-01-01T00:00:00Z"}""")
        )
        
        val request = JsonBodyPostSample.CreateItemRequest(
            name = "Widget",
            description = "Test widget",
            price = 25.99,
            tags = listOf("tag1")
        )
        
        // Act
        itemService.createItem(request)
        
        // Assert: Verify Content-Type
        val recordedRequest = mockWebServer.takeRequest()
        val contentType = recordedRequest.getHeader("Content-Type")
        assertNotNull("Content-Type header should be present", contentType)
        assertTrue(
            "Content-Type should be application/json",
            contentType!!.contains("application/json")
        )
    }
    
    /**
     * Test that request body contains properly serialized JSON.
     */
    @Test
    fun `createItem sends properly formatted JSON body`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"id":3,"name":"Gadget","description":"Cool gadget","price":99.99,"tags":["electronics","new"],"createdAt":"2024-01-01T00:00:00Z"}""")
        )
        
        val request = JsonBodyPostSample.CreateItemRequest(
            name = "Gadget",
            description = "Cool gadget",
            price = 99.99,
            tags = listOf("electronics", "new")
        )
        
        // Act
        itemService.createItem(request)
        
        // Assert: Verify JSON body
        val recordedRequest = mockWebServer.takeRequest()
        val bodyString = recordedRequest.body.readUtf8()
        
        // Verify body contains expected fields
        assertTrue("Body should contain name", bodyString.contains("\"name\""))
        assertTrue("Body should contain Gadget", bodyString.contains("\"Gadget\""))
        assertTrue("Body should contain description", bodyString.contains("\"description\""))
        assertTrue("Body should contain Cool gadget", bodyString.contains("\"Cool gadget\""))
        assertTrue("Body should contain price", bodyString.contains("\"price\""))
        assertTrue("Body should contain 99.99", bodyString.contains("99.99"))
        assertTrue("Body should contain tags", bodyString.contains("\"tags\""))
        assertTrue("Body should contain electronics", bodyString.contains("\"electronics\""))
        assertTrue("Body should contain new", bodyString.contains("\"new\""))
    }
    
    /**
     * Test that response is properly deserialized.
     */
    @Test
    fun `createItem deserializes response correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"id":42,"name":"Product","description":"A product","price":19.99,"tags":["sale","featured"],"createdAt":"2024-01-15T10:30:00Z"}""")
        )
        
        val request = JsonBodyPostSample.CreateItemRequest(
            name = "Product",
            description = "A product",
            price = 19.99,
            tags = listOf("sale", "featured")
        )
        
        // Act
        val response = itemService.createItem(request)
        
        // Assert: Verify deserialized response
        assertEquals(42L, response.id)
        assertEquals("Product", response.name)
        assertEquals("A product", response.description)
        assertEquals(19.99, response.price, 0.001)
        assertEquals(listOf("sale", "featured"), response.tags)
        assertEquals("2024-01-15T10:30:00Z", response.createdAt)
    }
}

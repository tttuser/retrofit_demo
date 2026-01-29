package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * RequestShapeSuiteTest: Tests for RequestShapeSuite sample.
 * 
 * Test strategy:
 * 1. Test various request parameter types
 * 2. Verify correct HTTP methods
 * 3. Validate query, path, header, and body parameters
 * 4. Test parameter combinations
 */
class RequestShapeSuiteTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: RequestShapeSuite.RequestShapeService
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val retrofit = RequestShapeSuite.createRetrofit(mockWebServer.url("/").toString())
        service = RequestShapeSuite.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test GET with path parameter.
     */
    @Test
    fun `getItem sends GET with path parameter`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":123,"name":"Item 123"}""")
        )
        
        // Act
        service.getItem(123)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertTrue("Path should contain /items/123", request.path?.contains("/items/123") == true)
    }
    
    /**
     * Test GET with multiple query parameters.
     */
    @Test
    fun `listItems sends query parameters`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[]""")
        )
        
        // Act
        service.listItems(category = "electronics", limit = 20, offset = 10)
        
        // Assert
        val request = mockWebServer.takeRequest()
        val path = request.path ?: ""
        assertTrue("Should have category parameter", path.contains("category=electronics"))
        assertTrue("Should have limit parameter", path.contains("limit=20"))
        assertTrue("Should have offset parameter", path.contains("offset=10"))
    }
    
    /**
     * Test GET with query map.
     */
    @Test
    fun `searchItems sends query map parameters`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[]""")
        )
        
        val filters = mapOf(
            "brand" to "Apple",
            "price_min" to "100",
            "price_max" to "500"
        )
        
        // Act
        service.searchItems(filters)
        
        // Assert
        val request = mockWebServer.takeRequest()
        val path = request.path ?: ""
        assertTrue("Should have brand parameter", path.contains("brand=Apple"))
        assertTrue("Should have price_min parameter", path.contains("price_min=100"))
        assertTrue("Should have price_max parameter", path.contains("price_max=500"))
    }
    
    /**
     * Test POST with JSON body.
     */
    @Test
    fun `createItem sends POST with JSON body`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"id":1,"name":"New Item","category":"test"}""")
        )
        
        val item = RequestShapeSuite.Item(name = "New Item", category = "test")
        
        // Act
        service.createItem(item)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/items", request.path)
        
        val body = request.body.readUtf8()
        assertTrue("Body should contain name", body.contains("New Item"))
        assertTrue("Body should contain category", body.contains("test"))
    }
    
    /**
     * Test POST with form-encoded body.
     */
    @Test
    fun `createItemForm sends form-encoded data`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Created"}""")
        )
        
        // Act
        service.createItemForm(name = "Item Name", category = "Category")
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type"))
        
        val body = request.body.readUtf8()
        assertTrue("Body should contain name", body.contains("name=Item"))
        assertTrue("Body should contain category", body.contains("category=Category"))
    }
    
    /**
     * Test POST with field map.
     */
    @Test
    fun `createItemFormMap sends field map as form data`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Created"}""")
        )
        
        val fields = mapOf(
            "name" to "Dynamic Item",
            "category" to "Dynamic Category"
        )
        
        // Act
        service.createItemFormMap(fields)
        
        // Assert
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue("Body should contain name", body.contains("name=Dynamic"))
        assertTrue("Body should contain category", body.contains("category=Dynamic"))
    }
    
    /**
     * Test PUT with path and body.
     */
    @Test
    fun `updateItem sends PUT with path and body`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":42,"name":"Updated Item"}""")
        )
        
        val item = RequestShapeSuite.Item(id = 42, name = "Updated Item")
        
        // Act
        service.updateItem(id = 42, item = item)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue("Path should contain /items/42", request.path?.contains("/items/42") == true)
        
        val body = request.body.readUtf8()
        assertTrue("Body should contain Updated Item", body.contains("Updated Item"))
    }
    
    /**
     * Test DELETE with path parameter.
     */
    @Test
    fun `deleteItem sends DELETE request`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Deleted"}""")
        )
        
        // Act
        service.deleteItem(99)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("DELETE", request.method)
        assertTrue("Path should contain /items/99", request.path?.contains("/items/99") == true)
    }
    
    /**
     * Test GET with custom headers.
     */
    @Test
    fun `listItemsWithAuth sends custom headers`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[]""")
        )
        
        // Act
        service.listItemsWithAuth(token = "Bearer abc123", version = "1.0.0")
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("Bearer abc123", request.getHeader("Authorization"))
        assertEquals("1.0.0", request.getHeader("X-Client-Version"))
    }
    
    /**
     * Test GET with header map.
     */
    @Test
    fun `listItemsWithHeaders sends header map`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[]""")
        )
        
        val headers = mapOf(
            "X-Custom-Header" to "custom-value",
            "X-Request-Id" to "req-123"
        )
        
        // Act
        service.listItemsWithHeaders(headers)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("custom-value", request.getHeader("X-Custom-Header"))
        assertEquals("req-123", request.getHeader("X-Request-Id"))
    }
    
    /**
     * Test complex request combining multiple parameter types.
     */
    @Test
    fun `complexUpdate combines path, query, header, and body`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Updated"}""")
        )
        
        val item = RequestShapeSuite.Item(id = 10, name = "Complex Update")
        
        // Act
        service.complexUpdate(
            id = 10,
            validate = true,
            source = "mobile-app",
            item = item
        )
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        
        // Check path
        assertTrue("Path should contain /items/10/update", request.path?.contains("/items/10/update") == true)
        
        // Check query
        assertTrue("Query should contain validate=true", request.path?.contains("validate=true") == true)
        
        // Check header
        assertEquals("mobile-app", request.getHeader("X-Request-Source"))
        
        // Check body
        val body = request.body.readUtf8()
        assertTrue("Body should contain Complex Update", body.contains("Complex Update"))
    }
}

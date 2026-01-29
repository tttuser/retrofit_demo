package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ConverterSwapLabTest: Tests for ConverterSwapLab sample.
 * 
 * Test strategy:
 * 1. Test different converter configurations
 * 2. Verify converter selection for different return types
 * 3. Test converter precedence
 * 4. Validate utility functions
 */
class ConverterSwapLabTest {
    
    private lateinit var mockWebServer: MockWebServer
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test Retrofit with JSON-only converter.
     */
    @Test
    fun `JSON-only Retrofit handles JSON responses`() = runBlocking {
        // Arrange
        val retrofit = ConverterSwapLab.createRetrofitJsonOnly(mockWebServer.url("/").toString())
        val service = retrofit.create(ConverterSwapLab.ConverterTestService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"test","count":42}""")
        )
        
        // Act
        val result = service.getJson()
        
        // Assert
        assertEquals("test", result.value)
        assertEquals(42, result.count)
    }
    
    /**
     * Test Retrofit with both converters handles String.
     */
    @Test
    fun `Retrofit with both converters handles String responses`() = runBlocking {
        // Arrange
        val retrofit = ConverterSwapLab.createRetrofitWithBothConverters(mockWebServer.url("/").toString())
        val service = retrofit.create(ConverterSwapLab.ConverterTestService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Plain text response")
        )
        
        // Act
        val result = service.getText()
        
        // Assert
        assertEquals("Plain text response", result)
    }
    
    /**
     * Test Retrofit with both converters handles JSON.
     */
    @Test
    fun `Retrofit with both converters handles JSON responses`() = runBlocking {
        // Arrange
        val retrofit = ConverterSwapLab.createRetrofitWithBothConverters(mockWebServer.url("/").toString())
        val service = retrofit.create(ConverterSwapLab.ConverterTestService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"json test","count":99}""")
        )
        
        // Act
        val result = service.getJson()
        
        // Assert
        assertEquals("json test", result.value)
        assertEquals(99, result.count)
    }
    
    /**
     * Test posting String with both converters.
     */
    @Test
    fun `can post String and receive JSON with both converters`() = runBlocking {
        // Arrange
        val retrofit = ConverterSwapLab.createRetrofitWithBothConverters(mockWebServer.url("/").toString())
        val service = retrofit.create(ConverterSwapLab.ConverterTestService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"echoed","count":1}""")
        )
        
        // Act
        val result = service.echoText("test text")
        
        // Assert
        assertEquals("echoed", result.value)
        assertEquals(1, result.count)
        
        // Verify request body
        val request = mockWebServer.takeRequest()
        assertEquals("test text", request.body.readUtf8())
    }
    
    /**
     * Test reversed converter order.
     */
    @Test
    fun `reversed converter order handles JSON`() = runBlocking {
        // Arrange
        val retrofit = ConverterSwapLab.createRetrofitReversedOrder(mockWebServer.url("/").toString())
        val service = retrofit.create(ConverterSwapLab.ConverterTestService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"reversed","count":7}""")
        )
        
        // Act
        val result = service.getJson()
        
        // Assert
        assertEquals("reversed", result.value)
        assertEquals(7, result.count)
    }
    
    /**
     * Test reversed converter order handles String.
     */
    @Test
    fun `reversed converter order handles String`() = runBlocking {
        // Arrange
        val retrofit = ConverterSwapLab.createRetrofitReversedOrder(mockWebServer.url("/").toString())
        val service = retrofit.create(ConverterSwapLab.ConverterTestService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Plain text in reversed order")
        )
        
        // Act
        val result = service.getText()
        
        // Assert
        assertEquals("Plain text in reversed order", result)
    }
    
    /**
     * Test getConverterInfo utility.
     */
    @Test
    fun `getConverterInfo returns configuration information`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit = ConverterSwapLab.createRetrofitWithBothConverters(baseUrl)
        
        // Act
        val info = ConverterSwapLab.getConverterInfo(retrofit)
        
        // Assert
        assertNotNull("Should have baseUrl", info["baseUrl"])
        assertEquals("BaseUrl should match", baseUrl, info["baseUrl"])
        assertNotNull("Should have note", info["note"])
    }
    
    /**
     * Test predictConverter utility for String.
     */
    @Test
    fun `predictConverter identifies String converter`() {
        // Act
        val prediction = ConverterSwapLab.predictConverter(
            returnType = "String",
            hasScalars = true,
            hasMoshi = true,
            scalarsFirst = true
        )
        
        // Assert
        assertTrue(
            "Should predict ScalarsConverterFactory for String",
            prediction.contains("ScalarsConverterFactory")
        )
    }
    
    /**
     * Test predictConverter utility for JSON object.
     */
    @Test
    fun `predictConverter identifies JSON object converter`() {
        // Act
        val prediction = ConverterSwapLab.predictConverter(
            returnType = "JsonObject",
            hasScalars = true,
            hasMoshi = true,
            scalarsFirst = true
        )
        
        // Assert
        assertTrue(
            "Should predict MoshiConverterFactory for JsonObject",
            prediction.contains("MoshiConverterFactory")
        )
    }
    
    /**
     * Test predictConverter when only Moshi is available.
     */
    @Test
    fun `predictConverter with only Moshi for String`() {
        // Act
        val prediction = ConverterSwapLab.predictConverter(
            returnType = "String",
            hasScalars = false,
            hasMoshi = true,
            scalarsFirst = false
        )
        
        // Assert
        assertTrue(
            "Should mention MoshiConverterFactory when only Moshi available",
            prediction.contains("MoshiConverterFactory")
        )
    }
    
    /**
     * Test predictConverter with no converters.
     */
    @Test
    fun `predictConverter with no converters`() {
        // Act
        val prediction = ConverterSwapLab.predictConverter(
            returnType = "String",
            hasScalars = false,
            hasMoshi = false,
            scalarsFirst = false
        )
        
        // Assert
        assertTrue(
            "Should indicate no suitable converter",
            prediction.contains("No suitable converter")
        )
    }
    
    /**
     * Test that different Retrofit instances work correctly.
     */
    @Test
    fun `different Retrofit configurations are independent`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val retrofit1 = ConverterSwapLab.createRetrofitJsonOnly(baseUrl)
        val retrofit2 = ConverterSwapLab.createRetrofitWithBothConverters(baseUrl)
        val retrofit3 = ConverterSwapLab.createRetrofitReversedOrder(baseUrl)
        
        // Act & Assert: All should be created successfully
        assertNotNull("JSON-only Retrofit should be created", retrofit1)
        assertNotNull("Both converters Retrofit should be created", retrofit2)
        assertNotNull("Reversed order Retrofit should be created", retrofit3)
        
        // All should have same base URL
        assertEquals(baseUrl, retrofit1.baseUrl().toString())
        assertEquals(baseUrl, retrofit2.baseUrl().toString())
        assertEquals(baseUrl, retrofit3.baseUrl().toString())
    }
    
    /**
     * Test content-type header for String request.
     */
    @Test
    fun `String body has correct content type`() = runBlocking {
        // Arrange
        val retrofit = ConverterSwapLab.createRetrofitWithBothConverters(mockWebServer.url("/").toString())
        val service = retrofit.create(ConverterSwapLab.ConverterTestService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"response","count":1}""")
        )
        
        // Act
        service.echoText("test")
        
        // Assert
        val request = mockWebServer.takeRequest()
        val contentType = request.getHeader("Content-Type")
        assertNotNull("Should have Content-Type header", contentType)
        assertTrue(
            "Content-Type should be text/plain for String",
            contentType?.contains("text/plain") == true
        )
    }
}

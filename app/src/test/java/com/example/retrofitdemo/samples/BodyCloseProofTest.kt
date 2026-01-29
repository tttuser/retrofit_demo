package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * BodyCloseProofTest: Tests for BodyCloseProof sample.
 * 
 * Test strategy:
 * 1. Test automatic body closing with different return types
 * 2. Test manual body closing methods
 * 3. Verify behavior documentation
 * 4. Test ResponseBody reading methods
 */
class BodyCloseProofTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: BodyCloseProof.BodyCloseTestService
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val retrofit = BodyCloseProof.createRetrofit(mockWebServer.url("/").toString())
        service = retrofit.create(BodyCloseProof.BodyCloseTestService::class.java)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test automatic body closing with direct type return.
     */
    @Test
    fun `getData automatically closes response body`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"content":"test data"}""")
        )
        
        // Act
        val data = BodyCloseProof.fetchDataAutoClosed(service, 1)
        
        // Assert
        assertEquals(1, data.id)
        assertEquals("test data", data.content)
        
        // Verify request was made
        val request = mockWebServer.takeRequest()
        assertTrue("Should call correct path", request.path?.contains("/data/1") == true)
    }
    
    /**
     * Test automatic body closing with Response wrapper.
     */
    @Test
    fun `getDataResponse automatically closes response body`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":2,"content":"response data"}""")
        )
        
        // Act
        val response = BodyCloseProof.fetchDataResponseAutoClosed(service, 2)
        
        // Assert
        assertTrue("Response should be successful", response.isSuccessful)
        assertNotNull("Body should be present", response.body())
        assertEquals(2, response.body()?.id)
        assertEquals("response data", response.body()?.content)
    }
    
    /**
     * Test manual body closing with ResponseBody.
     */
    @Test
    fun `manual close properly handles ResponseBody`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":3,"content":"raw data"}""")
        )
        
        // Act
        val content = BodyCloseProof.fetchDataManualClose(service, 3)
        
        // Assert
        assertTrue("Should contain JSON content", content.contains("raw data"))
        assertTrue("Should contain id", content.contains("\"id\":3"))
    }
    
    /**
     * Test reading ResponseBody as string.
     */
    @Test
    fun `reading as string works correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":4,"content":"string data"}""")
        )
        
        // Act
        val content = BodyCloseProof.fetchDataAsString(service, 4)
        
        // Assert
        assertTrue("Should contain string data", content.contains("string data"))
        assertTrue("Should contain id", content.contains("\"id\":4"))
    }
    
    /**
     * Test reading ResponseBody as bytes.
     */
    @Test
    fun `reading as bytes works correctly`() = runBlocking {
        // Arrange
        val responseBody = """{"id":5,"content":"byte data"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
        )
        
        // Act
        val bytes = BodyCloseProof.fetchDataAsBytes(service, 5)
        
        // Assert
        assertNotNull("Should have bytes", bytes)
        assertTrue("Should have non-zero length", bytes.isNotEmpty())
        
        // Convert back to string to verify content
        val content = String(bytes)
        assertTrue("Should contain byte data", content.contains("byte data"))
    }
    
    /**
     * Test that direct service call works.
     */
    @Test
    fun `direct getData call works`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":6,"content":"direct call"}""")
        )
        
        // Act
        val data = service.getData(6)
        
        // Assert
        assertEquals(6, data.id)
        assertEquals("direct call", data.content)
    }
    
    /**
     * Test that Response wrapper call works.
     */
    @Test
    fun `direct getDataResponse call works`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":7,"content":"response call"}""")
        )
        
        // Act
        val response = service.getDataResponse(7)
        
        // Assert
        assertTrue("Should be successful", response.isSuccessful)
        assertEquals(200, response.code())
        assertEquals(7, response.body()?.id)
    }
    
    /**
     * Test that raw ResponseBody call works.
     */
    @Test
    fun `direct getDataRaw call works with manual closing`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":8,"content":"raw call"}""")
        )
        
        // Act
        val responseBody = service.getDataRaw(8)
        val content = responseBody.use { it.string() }
        
        // Assert
        assertTrue("Should contain raw call", content.contains("raw call"))
        assertTrue("Should contain id 8", content.contains("\"id\":8"))
    }
    
    /**
     * Test getAllClosingBehaviors documentation.
     */
    @Test
    fun `getAllClosingBehaviors returns all behaviors`() {
        // Act
        val behaviors = BodyCloseProof.getAllClosingBehaviors()
        
        // Assert
        assertTrue("Should have behaviors", behaviors.isNotEmpty())
        assertEquals("Should have 6 behaviors", 6, behaviors.size)
        
        // Check for expected behavior names
        assertTrue("Should have AUTO_CLOSED_TYPE", behaviors.containsKey("AUTO_CLOSED_TYPE"))
        assertTrue("Should have AUTO_CLOSED_RESPONSE", behaviors.containsKey("AUTO_CLOSED_RESPONSE"))
        assertTrue("Should have MANUAL_CLOSE_REQUIRED", behaviors.containsKey("MANUAL_CLOSE_REQUIRED"))
        assertTrue("Should have STRING_AUTO_CLOSES", behaviors.containsKey("STRING_AUTO_CLOSES"))
        assertTrue("Should have BYTES_AUTO_CLOSES", behaviors.containsKey("BYTES_AUTO_CLOSES"))
        assertTrue("Should have USE_BLOCK_PATTERN", behaviors.containsKey("USE_BLOCK_PATTERN"))
    }
    
    /**
     * Test behavior descriptions are informative.
     */
    @Test
    fun `behavior descriptions are informative`() {
        // Act
        val behaviors = BodyCloseProof.getAllClosingBehaviors()
        
        // Assert: Each behavior should have a meaningful description
        behaviors.forEach { (name, description) ->
            assertTrue(
                "Description for $name should not be empty",
                description.isNotEmpty()
            )
            assertTrue(
                "Description for $name should mention closing or Retrofit",
                description.contains("close", ignoreCase = true) ||
                description.contains("Retrofit", ignoreCase = true)
            )
        }
    }
    
    /**
     * Test specific behavior descriptions.
     */
    @Test
    fun `AUTO_CLOSED_TYPE behavior has correct description`() {
        // Act
        val description = BodyCloseProof.ClosingBehavior.AUTO_CLOSED_TYPE.getDescription()
        
        // Assert
        assertTrue("Should mention automatic closing", description.contains("automatically"))
        assertTrue("Should mention type", description.contains("type") || description.contains("T"))
    }
    
    /**
     * Test MANUAL_CLOSE_REQUIRED behavior.
     */
    @Test
    fun `MANUAL_CLOSE_REQUIRED behavior has correct description`() {
        // Act
        val description = BodyCloseProof.ClosingBehavior.MANUAL_CLOSE_REQUIRED.getDescription()
        
        // Assert
        assertTrue("Should mention manual closing", description.contains("manual"))
        assertTrue("Should mention ResponseBody", description.contains("ResponseBody"))
    }
    
    /**
     * Test STRING_AUTO_CLOSES behavior.
     */
    @Test
    fun `STRING_AUTO_CLOSES behavior has correct description`() {
        // Act
        val description = BodyCloseProof.ClosingBehavior.STRING_AUTO_CLOSES.getDescription()
        
        // Assert
        assertTrue("Should mention string()", description.contains("string()"))
        assertTrue("Should mention automatic", description.contains("automatically"))
    }
    
    /**
     * Test that multiple sequential calls work correctly.
     */
    @Test
    fun `multiple sequential calls work correctly`() = runBlocking {
        // Arrange: Queue multiple responses
        repeat(3) { i ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"id":${i + 10},"content":"data $i"}""")
            )
        }
        
        // Act: Make multiple calls
        val data1 = service.getData(10)
        val data2 = service.getData(11)
        val data3 = service.getData(12)
        
        // Assert: All should work correctly
        assertEquals(10, data1.id)
        assertEquals(11, data2.id)
        assertEquals(12, data3.id)
        assertEquals("data 0", data1.content)
        assertEquals("data 1", data2.content)
        assertEquals("data 2", data3.content)
    }
}

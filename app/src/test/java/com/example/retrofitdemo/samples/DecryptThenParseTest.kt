package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * DecryptThenParseTest: Tests for DecryptThenParse sample.
 * 
 * Test strategy:
 * 1. Test decryption and parsing of encrypted response
 * 2. Test handling of plain (non-encrypted) responses
 * 3. Test list responses
 * 4. Test that decryption happens before parsing
 */
class DecryptThenParseTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: DecryptThenParse.SecureService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit with DecryptingConverterFactory
        val retrofit = DecryptThenParse.createRetrofit(mockWebServer.url("/").toString())
        service = DecryptThenParse.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test decrypting and parsing encrypted response.
     */
    @Test
    fun `decrypts and parses encrypted response`() = runBlocking {
        // Arrange: Encrypt JSON response
        val json = """{"id":1,"sensitiveInfo":"Secret data","timestamp":1234567890}"""
        val encrypted = DecryptThenParse.encryptForTesting(json)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(encrypted)
        )
        
        // Act
        val data = service.getSecureData()
        
        // Assert: Data should be decrypted and parsed
        assertEquals(1, data.id)
        assertEquals("Secret data", data.sensitiveInfo)
        assertEquals(1234567890L, data.timestamp)
    }
    
    /**
     * Test handling plain (non-encrypted) JSON responses.
     * Decryptor should gracefully handle non-encrypted data.
     */
    @Test
    fun `handles plain JSON when decryption fails`() = runBlocking {
        // Arrange: Plain JSON (not encrypted)
        val json = """{"id":2,"sensitiveInfo":"Plain data","timestamp":9876543210}"""
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
        )
        
        // Act
        val data = service.getSecureData()
        
        // Assert: Should parse successfully even without encryption
        assertEquals(2, data.id)
        assertEquals("Plain data", data.sensitiveInfo)
        assertEquals(9876543210L, data.timestamp)
    }
    
    /**
     * Test decrypting and parsing list of encrypted items.
     */
    @Test
    fun `decrypts and parses list of encrypted items`() = runBlocking {
        // Arrange: Encrypt JSON array
        val json = """[
            {"id":1,"sensitiveInfo":"Item 1","timestamp":1000},
            {"id":2,"sensitiveInfo":"Item 2","timestamp":2000}
        ]"""
        val encrypted = DecryptThenParse.encryptForTesting(json)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(encrypted)
        )
        
        // Act
        val list = service.getSecureList()
        
        // Assert: List should be decrypted and parsed
        assertEquals(2, list.size)
        assertEquals(1, list[0].id)
        assertEquals("Item 1", list[0].sensitiveInfo)
        assertEquals(2, list[1].id)
        assertEquals("Item 2", list[1].sensitiveInfo)
    }
    
    /**
     * Test that Base64Decryptor correctly decodes Base64.
     */
    @Test
    fun `Base64Decryptor correctly decodes Base64 strings`() {
        // Arrange
        val decryptor = DecryptThenParse.Base64Decryptor()
        val original = "Hello, World!"
        val encrypted = DecryptThenParse.encryptForTesting(original)
        
        // Act
        val decrypted = decryptor.decrypt(encrypted)
        
        // Assert
        assertEquals(original, decrypted)
    }
    
    /**
     * Test that Base64Decryptor handles invalid Base64 gracefully.
     */
    @Test
    fun `Base64Decryptor returns original on invalid Base64`() {
        // Arrange
        val decryptor = DecryptThenParse.Base64Decryptor()
        val invalid = "Not valid Base64!"
        
        // Act
        val result = decryptor.decrypt(invalid)
        
        // Assert: Should return original when decryption fails
        assertEquals(invalid, result)
    }
    
    /**
     * Test encryptForTesting produces valid Base64.
     */
    @Test
    fun `encryptForTesting produces valid Base64`() {
        // Arrange
        val json = """{"test":"data"}"""
        
        // Act
        val encrypted = DecryptThenParse.encryptForTesting(json)
        
        // Assert: Should be valid Base64 (can be decoded)
        val decryptor = DecryptThenParse.Base64Decryptor()
        val decrypted = decryptor.decrypt(encrypted)
        assertEquals(json, decrypted)
    }
    
    /**
     * Test that encrypted response contains no readable JSON.
     */
    @Test
    fun `encrypted response does not contain readable JSON`() {
        // Arrange
        val json = """{"secret":"value"}"""
        val encrypted = DecryptThenParse.encryptForTesting(json)
        
        // Assert: Encrypted string should not contain original JSON
        assertFalse("Encrypted should not contain 'secret'", encrypted.contains("secret"))
        assertFalse("Encrypted should not contain 'value'", encrypted.contains("value"))
        assertFalse("Encrypted should not contain '{'", encrypted.contains("{"))
    }
    
    /**
     * Test request is sent correctly (no encryption on request side).
     */
    @Test
    fun `requests are sent without encryption`() = runBlocking {
        // Arrange
        val json = """{"id":3,"sensitiveInfo":"Data","timestamp":3000}"""
        val encrypted = DecryptThenParse.encryptForTesting(json)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(encrypted)
        )
        
        // Act
        service.getSecureData()
        val request = mockWebServer.takeRequest()
        
        // Assert: Request should be normal GET (no body)
        assertEquals("GET", request.method)
        assertEquals("/secure/data", request.path)
    }
    
    /**
     * Test use cases are properly defined.
     */
    @Test
    fun `use cases are properly defined`() {
        // Act
        val useCases = DecryptThenParse.getUseCases()
        
        // Assert
        assertTrue("Should have at least 5 use cases", useCases.size >= 5)
        
        val descriptions = useCases.map { it.description }
        assertTrue("Should include sensitive data use case",
            descriptions.any { it.contains("sensitive") || it.contains("PII") })
        assertTrue("Should include compliance use case",
            descriptions.any { it.contains("compliance") || it.contains("GDPR") })
        assertTrue("Should include security use case",
            descriptions.any { it.contains("security") || it.contains("MITM") })
    }
    
    /**
     * Test that converter factory is properly integrated.
     */
    @Test
    fun `converter factory correctly integrates with Retrofit`() = runBlocking {
        // Arrange: Complex nested JSON
        val json = """{"id":99,"sensitiveInfo":"Complex test","timestamp":9999}"""
        val encrypted = DecryptThenParse.encryptForTesting(json)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(encrypted)
        )
        
        // Act
        val data = service.getSecureData()
        
        // Assert: Should decrypt and parse correctly
        assertEquals(99, data.id)
        assertEquals("Complex test", data.sensitiveInfo)
        assertEquals(9999L, data.timestamp)
    }
}

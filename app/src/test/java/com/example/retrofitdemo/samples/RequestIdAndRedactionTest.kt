package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * RequestIdAndRedactionTest: Tests for RequestIdAndRedaction sample.
 * 
 * Test strategy:
 * 1. Verify X-Request-Id header is added to requests
 * 2. Test that request ID is a valid UUID format
 * 3. Test redaction of sensitive fields in logs
 * 4. Verify request ID is preserved across interceptors
 * 5. Test that non-sensitive data is not redacted
 */
class RequestIdAndRedactionTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: RequestIdAndRedaction.TestService
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val retrofit = RequestIdAndRedaction.createRetrofit(mockWebServer.url("/").toString())
        service = retrofit.create(RequestIdAndRedaction.TestService::class.java)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that X-Request-Id header is added to all requests.
     */
    @Test
    fun `X-Request-Id header is added to requests`() = runBlocking {
        // Arrange: Mock response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok"}""")
        )
        
        // Act: Make request
        service.getData()
        
        // Assert: Verify request has X-Request-Id header
        val request = mockWebServer.takeRequest()
        val requestId = request.getHeader("X-Request-Id")
        
        assertNotNull("X-Request-Id header should be present", requestId)
    }
    
    /**
     * Test that X-Request-Id has valid UUID format.
     */
    @Test
    fun `X-Request-Id has valid UUID format`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok"}""")
        )
        
        // Act
        service.getData()
        
        // Assert: Verify UUID format (8-4-4-4-12 hex digits)
        val request = mockWebServer.takeRequest()
        val requestId = request.getHeader("X-Request-Id")
        
        assertNotNull("X-Request-Id should be present", requestId)
        
        val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertTrue(
            "X-Request-Id should be a valid UUID: $requestId",
            requestId!!.matches(uuidPattern)
        )
    }
    
    /**
     * Test that each request gets a unique request ID.
     */
    @Test
    fun `each request gets unique request ID`() = runBlocking {
        // Arrange: Mock multiple responses
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"status":"ok"}""")
            )
        }
        
        // Act: Make multiple requests
        service.getData()
        service.getData()
        service.getData()
        
        // Assert: Each request should have different ID
        val requestId1 = mockWebServer.takeRequest().getHeader("X-Request-Id")
        val requestId2 = mockWebServer.takeRequest().getHeader("X-Request-Id")
        val requestId3 = mockWebServer.takeRequest().getHeader("X-Request-Id")
        
        assertNotNull(requestId1)
        assertNotNull(requestId2)
        assertNotNull(requestId3)
        
        // All IDs should be different
        assertNotEquals("IDs should be unique", requestId1, requestId2)
        assertNotEquals("IDs should be unique", requestId2, requestId3)
        assertNotEquals("IDs should be unique", requestId1, requestId3)
    }
    
    /**
     * Test redaction of password field.
     */
    @Test
    fun `password field is redacted in logs`() {
        // Arrange: Create interceptor
        val interceptor = RequestIdAndRedaction.RedactingLoggingInterceptor()
        
        // Test data with password
        val jsonWithPassword = """{"username":"john","password":"secret123"}"""
        
        // Act: Redact sensitive data
        val redacted = interceptor.redactSensitiveData(jsonWithPassword)
        
        // Assert: Password should be redacted
        assertFalse("Password should be redacted", redacted.contains("secret123"))
        assertTrue("Should contain redaction marker", redacted.contains("[REDACTED]"))
        assertTrue("Username should not be redacted", redacted.contains("john"))
    }
    
    /**
     * Test redaction of token field.
     */
    @Test
    fun `token field is redacted in logs`() {
        // Arrange: Create interceptor
        val interceptor = RequestIdAndRedaction.RedactingLoggingInterceptor()
        
        // Test data with token
        val jsonWithToken = """{"token":"abc123xyz","userId":42}"""
        
        // Act: Redact sensitive data
        val redacted = interceptor.redactSensitiveData(jsonWithToken)
        
        // Assert: Token should be redacted
        assertFalse("Token should be redacted", redacted.contains("abc123xyz"))
        assertTrue("Should contain redaction marker", redacted.contains("[REDACTED]"))
        assertTrue("userId should not be redacted", redacted.contains("42"))
    }
    
    /**
     * Test that multiple sensitive fields are redacted.
     */
    @Test
    fun `multiple sensitive fields are redacted`() {
        // Arrange: Create interceptor
        val interceptor = RequestIdAndRedaction.RedactingLoggingInterceptor()
        
        // Test data with multiple sensitive fields
        val jsonWithSensitive = """
            {
                "username":"alice",
                "password":"pass123",
                "token":"token456",
                "apiKey":"key789"
            }
        """.trimIndent()
        
        // Act: Redact sensitive data
        val redacted = interceptor.redactSensitiveData(jsonWithSensitive)
        
        // Assert: All sensitive fields should be redacted
        assertFalse("Password should be redacted", redacted.contains("pass123"))
        assertFalse("Token should be redacted", redacted.contains("token456"))
        assertFalse("ApiKey should be redacted", redacted.contains("key789"))
        assertTrue("Username should not be redacted", redacted.contains("alice"))
    }
    
    /**
     * Test that non-sensitive data is not redacted.
     */
    @Test
    fun `non-sensitive data is not redacted`() {
        // Arrange: Create interceptor
        val interceptor = RequestIdAndRedaction.RedactingLoggingInterceptor()
        
        // Test data without sensitive fields
        val json = """{"username":"bob","email":"bob@example.com","age":30}"""
        
        // Act: Redact sensitive data
        val redacted = interceptor.redactSensitiveData(json)
        
        // Assert: Non-sensitive data should remain unchanged
        assertTrue("Username should not be redacted", redacted.contains("bob"))
        assertTrue("Email should not be redacted", redacted.contains("bob@example.com"))
        assertTrue("Age should not be redacted", redacted.contains("30"))
    }
    
    /**
     * Test case-insensitive redaction.
     */
    @Test
    fun `redaction is case-insensitive`() {
        // Arrange: Create interceptor
        val interceptor = RequestIdAndRedaction.RedactingLoggingInterceptor()
        
        // Test with different case variations
        val testCases = listOf(
            """{"Password":"secret"}""",
            """{"PASSWORD":"secret"}""",
            """{"password":"secret"}""",
            """{"PaSsWoRd":"secret"}"""
        )
        
        testCases.forEach { json ->
            // Act: Redact sensitive data
            val redacted = interceptor.redactSensitiveData(json)
            
            // Assert: Should be redacted regardless of case
            assertFalse(
                "Password should be redacted in: $json",
                redacted.contains("secret")
            )
        }
    }
    
    /**
     * Test that login request works with interceptors.
     */
    @Test
    fun `login request works with interceptors`() = runBlocking {
        // Arrange: Mock login response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"auth-token-123","userId":1}""")
        )
        
        // Act: Make login request
        val loginRequest = RequestIdAndRedaction.LoginRequest(
            username = "testuser",
            password = "testpass"
        )
        val response = service.login(loginRequest)
        
        // Assert: Response should be received
        assertNotNull("Response should not be null", response)
        assertEquals("auth-token-123", response.token)
        assertEquals(1, response.userId)
        
        // Verify request has X-Request-Id
        val request = mockWebServer.takeRequest()
        assertNotNull(
            "X-Request-Id should be present",
            request.getHeader("X-Request-Id")
        )
    }
    
    /**
     * Test that interceptors preserve request body.
     */
    @Test
    fun `interceptors preserve request body content`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"xyz","userId":2}""")
        )
        
        // Act: Make login request
        val loginRequest = RequestIdAndRedaction.LoginRequest(
            username = "alice",
            password = "alicepass"
        )
        service.login(loginRequest)
        
        // Assert: Server should receive complete request body
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        
        assertTrue("Body should contain username", body.contains("alice"))
        assertTrue("Body should contain password", body.contains("alicepass"))
    }
}

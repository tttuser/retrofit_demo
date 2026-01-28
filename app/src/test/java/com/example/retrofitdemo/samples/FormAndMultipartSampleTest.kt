package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * FormAndMultipartSampleTest: Tests for FormAndMultipartSample.
 * 
 * Test strategy:
 * 1. Verify form-encoded requests have correct Content-Type
 * 2. Verify form fields are properly encoded
 * 3. Verify multipart requests have correct Content-Type with boundary
 * 4. Verify multipart parts are present in request
 */
class FormAndMultipartSampleTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var formService: FormAndMultipartSample.FormService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit and service
        val retrofit = FormAndMultipartSample.createRetrofit(mockWebServer.url("/").toString())
        formService = FormAndMultipartSample.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that form-encoded request has correct Content-Type.
     */
    @Test
    fun `login uses form-encoded content type`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"token":"abc123","userId":1}""")
        )
        
        // Act
        formService.login(username = "testuser", password = "testpass")
        
        // Assert: Verify Content-Type
        val request = mockWebServer.takeRequest()
        val contentType = request.getHeader("Content-Type")
        assertNotNull("Content-Type header should be present", contentType)
        assertEquals(
            "Content-Type should be application/x-www-form-urlencoded",
            "application/x-www-form-urlencoded",
            contentType
        )
    }
    
    /**
     * Test that form fields are properly encoded.
     */
    @Test
    fun `login encodes form fields correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"token":"xyz789","userId":2}""")
        )
        
        // Act
        formService.login(username = "alice", password = "secret123")
        
        // Assert: Verify form body
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue("Body should contain username field", body.contains("username=alice"))
        assertTrue("Body should contain password field", body.contains("password=secret123"))
    }
    
    /**
     * Test that register encodes multiple fields with different types.
     */
    @Test
    fun `register encodes multiple form fields with different types`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"success":true,"token":"newtoken","userId":3}""")
        )
        
        // Act
        formService.register(
            email = "test@example.com",
            username = "newuser",
            age = 25,
            agreedToTerms = true
        )
        
        // Assert: Verify all fields in body
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue("Body should contain email", body.contains("email=test"))
        assertTrue("Body should contain username", body.contains("username=newuser"))
        assertTrue("Body should contain age", body.contains("age=25"))
        assertTrue("Body should contain agreed_to_terms", body.contains("agreed_to_terms=true"))
    }
    
    /**
     * Test that multipart request has correct Content-Type with boundary.
     */
    @Test
    fun `uploadFile uses multipart content type with boundary`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"fileId":"file123","filename":"test.txt","size":100}""")
        )
        
        val filePart = FormAndMultipartSample.createFilePart(
            name = "file",
            filename = "test.txt",
            content = "Hello, World!".toByteArray()
        )
        
        // Act
        formService.uploadFile(file = filePart, description = "Test file")
        
        // Assert: Verify Content-Type
        val request = mockWebServer.takeRequest()
        val contentType = request.getHeader("Content-Type")
        assertNotNull("Content-Type header should be present", contentType)
        assertTrue(
            "Content-Type should start with multipart/form-data",
            contentType!!.startsWith("multipart/form-data")
        )
        assertTrue(
            "Content-Type should contain boundary",
            contentType.contains("boundary=")
        )
    }
    
    /**
     * Test that multipart request contains all parts.
     */
    @Test
    fun `uploadFile includes file part and description field`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"fileId":"file456","filename":"document.pdf","size":2048}""")
        )
        
        val fileContent = "Sample PDF content".toByteArray()
        val filePart = FormAndMultipartSample.createFilePart(
            name = "file",
            filename = "document.pdf",
            content = fileContent
        )
        
        // Act
        formService.uploadFile(file = filePart, description = "Important document")
        
        // Assert: Verify multipart body
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        
        // Check for multipart structure
        assertTrue("Body should contain file field", body.contains("name=\"file\""))
        assertTrue("Body should contain filename", body.contains("filename=\"document.pdf\""))
        assertTrue("Body should contain description field", body.contains("name=\"description\""))
        assertTrue("Body should contain description value", body.contains("Important document"))
        assertTrue("Body should contain file content", body.contains("Sample PDF content"))
    }
    
    /**
     * Test that multipart boundary separates parts correctly.
     */
    @Test
    fun `uploadFile uses boundary to separate multipart parts`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"fileId":"file789","filename":"image.jpg","size":5120}""")
        )
        
        val filePart = FormAndMultipartSample.createFilePart(
            name = "file",
            filename = "image.jpg",
            content = ByteArray(100) { it.toByte() }
        )
        
        // Act
        formService.uploadFile(file = filePart, description = "Profile picture")
        
        // Assert: Verify boundary usage
        val request = mockWebServer.takeRequest()
        val contentType = request.getHeader("Content-Type") ?: ""
        val body = request.body.readUtf8()
        
        // Extract boundary from Content-Type header
        val boundaryMatch = Regex("boundary=([^;]+)").find(contentType)
        assertNotNull("Should find boundary in Content-Type", boundaryMatch)
        
        val boundary = boundaryMatch!!.groupValues[1]
        assertTrue("Body should contain boundary markers", body.contains("--$boundary"))
    }
}

package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * AnnotationMisuseCasesTest: Tests for AnnotationMisuseCases sample.
 * 
 * Test strategy:
 * 1. Test correct annotation patterns
 * 2. Verify service creation utilities
 * 3. Validate mistake documentation
 * 4. Test helper functions
 */
class AnnotationMisuseCasesTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var retrofit: retrofit2.Retrofit
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        retrofit = AnnotationMisuseCases.createRetrofit(mockWebServer.url("/").toString())
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that correct form service can be created.
     */
    @Test
    fun `correct form service creation succeeds`() {
        // Act
        val canCreate = AnnotationMisuseCases.canCreateService(
            retrofit,
            AnnotationMisuseCases.CorrectFormService::class.java
        )
        
        // Assert
        assertTrue("Should be able to create correct form service", canCreate)
    }
    
    /**
     * Test that correct body service can be created.
     */
    @Test
    fun `correct body service creation succeeds`() {
        // Act
        val canCreate = AnnotationMisuseCases.canCreateService(
            retrofit,
            AnnotationMisuseCases.CorrectBodyService::class.java
        )
        
        // Assert
        assertTrue("Should be able to create correct body service", canCreate)
    }
    
    /**
     * Test that correct path service can be created.
     */
    @Test
    fun `correct path service creation succeeds`() {
        // Act
        val canCreate = AnnotationMisuseCases.canCreateService(
            retrofit,
            AnnotationMisuseCases.CorrectPathService::class.java
        )
        
        // Assert
        assertTrue("Should be able to create correct path service", canCreate)
    }
    
    /**
     * Test correct form-encoded request.
     */
    @Test
    fun `correct form encoded request works`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Created"}""")
        )
        
        val service = retrofit.create(AnnotationMisuseCases.CorrectFormService::class.java)
        
        // Act
        service.createUser(name = "John", email = "john@example.com")
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type"))
        
        val body = request.body.readUtf8()
        assertTrue("Body should contain name", body.contains("name=John"))
        assertTrue("Body should contain email", body.contains("email=john"))
    }
    
    /**
     * Test correct body request.
     */
    @Test
    fun `correct body request works`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Created"}""")
        )
        
        val service = retrofit.create(AnnotationMisuseCases.CorrectBodyService::class.java)
        val user = AnnotationMisuseCases.User(name = "Alice", email = "alice@example.com")
        
        // Act
        service.createUser(user)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        
        val body = request.body.readUtf8()
        assertTrue("Body should contain Alice", body.contains("Alice"))
        assertTrue("Body should contain email", body.contains("alice@example.com"))
    }
    
    /**
     * Test correct path parameter usage.
     */
    @Test
    fun `correct path parameter works`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":42,"name":"User 42","email":"user42@example.com"}""")
        )
        
        val service = retrofit.create(AnnotationMisuseCases.CorrectPathService::class.java)
        
        // Act
        service.getUser(42)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertTrue("Path should contain /users/42", request.path?.contains("/users/42") == true)
    }
    
    /**
     * Test multiple path parameters.
     */
    @Test
    fun `multiple path parameters work correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Found"}""")
        )
        
        val service = retrofit.create(AnnotationMisuseCases.CorrectPathService::class.java)
        
        // Act
        service.getUserPost(userId = 10, postId = 20)
        
        // Assert
        val request = mockWebServer.takeRequest()
        val path = request.path ?: ""
        assertTrue("Path should contain /users/10", path.contains("/users/10"))
        assertTrue("Path should contain /posts/20", path.contains("/posts/20"))
    }
    
    /**
     * Test mixing different parameter types.
     */
    @Test
    fun `mixing parameters works correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Updated"}""")
        )
        
        val service = retrofit.create(AnnotationMisuseCases.CorrectMixedParametersService::class.java)
        val user = AnnotationMisuseCases.User(id = 5, name = "Updated", email = "updated@example.com")
        
        // Act
        service.updateUser(id = 5, notify = true, user = user)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertTrue("Path should contain /users/5", request.path?.contains("/users/5") == true)
        assertTrue("Path should contain notify=true", request.path?.contains("notify=true") == true)
        
        val body = request.body.readUtf8()
        assertTrue("Body should contain Updated", body.contains("Updated"))
    }
    
    /**
     * Test header with body.
     */
    @Test
    fun `header with body works correctly`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"message":"Created"}""")
        )
        
        val service = retrofit.create(AnnotationMisuseCases.CorrectMixedParametersService::class.java)
        val user = AnnotationMisuseCases.User(name = "Auth User", email = "auth@example.com")
        
        // Act
        service.createUserWithAuth(token = "Bearer token123", user = user)
        
        // Assert
        val request = mockWebServer.takeRequest()
        assertEquals("Bearer token123", request.getHeader("Authorization"))
        
        val body = request.body.readUtf8()
        assertTrue("Body should contain Auth User", body.contains("Auth User"))
    }
    
    /**
     * Test optional query parameters.
     */
    @Test
    fun `optional query parameters work`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[]""")
        )
        
        val service = retrofit.create(AnnotationMisuseCases.CorrectOptionalParametersService::class.java)
        
        // Act: Call with some optional parameters
        service.searchUsers(name = "John", email = null, limit = 5)
        
        // Assert
        val request = mockWebServer.takeRequest()
        val path = request.path ?: ""
        assertTrue("Path should contain name=John", path.contains("name=John"))
        assertFalse("Path should not contain email parameter", path.contains("email="))
        assertTrue("Path should contain limit=5", path.contains("limit=5"))
    }
    
    /**
     * Test getting all mistake advice.
     */
    @Test
    fun `getAllMistakeAdvice returns all mistakes`() {
        // Act
        val advice = AnnotationMisuseCases.getAllMistakeAdvice()
        
        // Assert
        assertTrue("Should have advice entries", advice.isNotEmpty())
        assertTrue("Should have MISSING_FORM_ENCODED advice", advice.containsKey("MISSING_FORM_ENCODED"))
        assertTrue("Should have BODY_WITH_FORM advice", advice.containsKey("BODY_WITH_FORM"))
        assertTrue("Should have PATH_MISMATCH advice", advice.containsKey("PATH_MISMATCH"))
        assertTrue("Should have MULTIPLE_BODIES advice", advice.containsKey("MULTIPLE_BODIES"))
        
        // Verify advice contains both description and solution
        val firstAdvice = advice["MISSING_FORM_ENCODED"] ?: ""
        assertTrue("Advice should contain description", firstAdvice.isNotEmpty())
        assertTrue("Advice should contain solution", firstAdvice.contains("Solution:"))
    }
    
    /**
     * Test that mistake enum has correct number of entries.
     */
    @Test
    fun `CommonMistake enum has all expected entries`() {
        // Act
        val advice = AnnotationMisuseCases.getAllMistakeAdvice()
        
        // Assert: Should have 6 common mistakes documented
        assertEquals("Should have 6 common mistakes", 6, advice.size)
    }
    
    /**
     * Test single mistake advice format.
     */
    @Test
    fun `mistake advice has correct format`() {
        // Act
        val pathMismatchAdvice = AnnotationMisuseCases.CommonMistake.PATH_MISMATCH.getAdvice()
        
        // Assert
        assertTrue("Should contain description", pathMismatchAdvice.contains("doesn't match"))
        assertTrue("Should contain solution", pathMismatchAdvice.contains("Solution:"))
        assertTrue("Should mention @Path", pathMismatchAdvice.contains("@Path"))
    }
}

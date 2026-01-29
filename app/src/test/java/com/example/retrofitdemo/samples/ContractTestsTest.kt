package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ContractTestsTest: Tests for ContractTests sample.
 * 
 * Test strategy:
 * 1. Test successful response contract validation
 * 2. Test error response contract validation
 * 3. Test user schema validation
 * 4. Test list validation
 * 5. Demonstrate contract testing patterns
 */
class ContractTestsTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: ContractTests.ContractService
    
    @Before
    fun setup() {
        // Create and start MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create Retrofit and service
        val retrofit = ContractTests.createRetrofit(mockWebServer.url("/").toString())
        service = ContractTests.createService(retrofit)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that valid successful response passes contract validation.
     */
    @Test
    fun `valid successful response passes contract validation`() = runBlocking {
        // Arrange: Valid response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":1,"username":"alice","email":"alice@example.com","createdAt":"2024-01-01"}""")
        )
        
        // Act
        val response = service.getUser(1)
        val result = ContractTests.validateSuccessContract(response)
        
        // Assert
        assertTrue("Valid response should pass validation", result is ContractTests.ValidationResult.Valid)
    }
    
    /**
     * Test that invalid status code fails contract validation.
     */
    @Test
    fun `invalid status code fails contract validation`() = runBlocking {
        // Arrange: Error status code
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"Not found"}""")
        )
        
        // Act
        val response = service.getUser(999)
        val result = ContractTests.validateSuccessContract(response)
        
        // Assert
        assertTrue("404 response should fail success validation", result is ContractTests.ValidationResult.Invalid)
        val errors = (result as ContractTests.ValidationResult.Invalid).errors
        assertTrue("Should mention status code", errors.any { it.contains("status code") })
    }
    
    /**
     * Test that missing Content-Type fails validation.
     */
    @Test
    fun `missing Content-Type fails contract validation`() = runBlocking {
        // Arrange: Response without Content-Type
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":2,"username":"bob","email":"bob@example.com","createdAt":"2024-01-01"}""")
                // No Content-Type header
        )
        
        // Act
        val response = service.getUser(2)
        val result = ContractTests.validateSuccessContract(response)
        
        // Assert
        assertTrue("Missing Content-Type should fail validation", result is ContractTests.ValidationResult.Invalid)
        val errors = (result as ContractTests.ValidationResult.Invalid).errors
        assertTrue("Should mention Content-Type", errors.any { it.contains("Content-Type") })
    }
    
    /**
     * Test error response contract validation.
     */
    @Test
    fun `valid error response passes error contract validation`() = runBlocking {
        // Arrange: Proper error response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"code":"NOT_FOUND","message":"User not found"}""")
        )
        
        // Act
        val response = service.getUser(999)
        val result = ContractTests.validateErrorContract(response)
        
        // Assert
        assertTrue("Valid error should pass error validation", result is ContractTests.ValidationResult.Valid)
    }
    
    /**
     * Test that 2xx response fails error contract validation.
     */
    @Test
    fun `success response fails error contract validation`() = runBlocking {
        // Arrange: Success response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":3,"username":"charlie","email":"charlie@example.com","createdAt":"2024-01-01"}""")
        )
        
        // Act
        val response = service.getUser(3)
        val result = ContractTests.validateErrorContract(response)
        
        // Assert
        assertTrue("Success response should fail error validation", result is ContractTests.ValidationResult.Invalid)
        val errors = (result as ContractTests.ValidationResult.Invalid).errors
        assertTrue("Should mention status code", errors.any { it.contains("status code") })
    }
    
    /**
     * Test user schema validation with valid user.
     */
    @Test
    fun `valid user passes schema validation`() {
        // Arrange
        val user = ContractTests.ApiUser(
            id = 1,
            username = "testuser",
            email = "test@example.com",
            createdAt = "2024-01-01T00:00:00Z"
        )
        
        // Act
        val result = ContractTests.validateUserSchema(user)
        
        // Assert
        assertTrue("Valid user should pass schema validation", result is ContractTests.ValidationResult.Valid)
    }
    
    /**
     * Test user schema validation with invalid ID.
     */
    @Test
    fun `user with invalid ID fails schema validation`() {
        // Arrange: User with ID <= 0
        val user = ContractTests.ApiUser(
            id = 0,
            username = "testuser",
            email = "test@example.com",
            createdAt = "2024-01-01"
        )
        
        // Act
        val result = ContractTests.validateUserSchema(user)
        
        // Assert
        assertTrue("Invalid ID should fail validation", result is ContractTests.ValidationResult.Invalid)
        val errors = (result as ContractTests.ValidationResult.Invalid).errors
        assertTrue("Should mention ID", errors.any { it.contains("ID") })
    }
    
    /**
     * Test user schema validation with empty username.
     */
    @Test
    fun `user with empty username fails schema validation`() {
        // Arrange
        val user = ContractTests.ApiUser(
            id = 1,
            username = "",
            email = "test@example.com",
            createdAt = "2024-01-01"
        )
        
        // Act
        val result = ContractTests.validateUserSchema(user)
        
        // Assert
        assertTrue("Empty username should fail validation", result is ContractTests.ValidationResult.Invalid)
        val errors = (result as ContractTests.ValidationResult.Invalid).errors
        assertTrue("Should mention username", errors.any { it.contains("username") || it.contains("Username") })
    }
    
    /**
     * Test user schema validation with invalid email.
     */
    @Test
    fun `user with invalid email fails schema validation`() {
        // Arrange: Email without @
        val user = ContractTests.ApiUser(
            id = 1,
            username = "testuser",
            email = "invalid-email",
            createdAt = "2024-01-01"
        )
        
        // Act
        val result = ContractTests.validateUserSchema(user)
        
        // Assert
        assertTrue("Invalid email should fail validation", result is ContractTests.ValidationResult.Invalid)
        val errors = (result as ContractTests.ValidationResult.Invalid).errors
        assertTrue("Should mention email", errors.any { it.contains("Email") || it.contains("email") })
    }
    
    /**
     * Test list validation with valid users.
     */
    @Test
    fun `valid user list passes validation`() {
        // Arrange
        val users = listOf(
            ContractTests.ApiUser(1, "alice", "alice@example.com", "2024-01-01"),
            ContractTests.ApiUser(2, "bob", "bob@example.com", "2024-01-02")
        )
        
        // Act
        val result = ContractTests.validateUserList(users)
        
        // Assert
        assertTrue("Valid list should pass validation", result is ContractTests.ValidationResult.Valid)
    }
    
    /**
     * Test list validation with duplicate IDs.
     */
    @Test
    fun `user list with duplicate IDs fails validation`() {
        // Arrange: Users with same ID
        val users = listOf(
            ContractTests.ApiUser(1, "alice", "alice@example.com", "2024-01-01"),
            ContractTests.ApiUser(1, "bob", "bob@example.com", "2024-01-02")
        )
        
        // Act
        val result = ContractTests.validateUserList(users)
        
        // Assert
        assertTrue("Duplicate IDs should fail validation", result is ContractTests.ValidationResult.Invalid)
        val errors = (result as ContractTests.ValidationResult.Invalid).errors
        assertTrue("Should mention unique IDs", errors.any { it.contains("unique") })
    }
    
    /**
     * Test list validation catches individual user errors.
     */
    @Test
    fun `user list validation catches individual user errors`() {
        // Arrange: One invalid user
        val users = listOf(
            ContractTests.ApiUser(1, "alice", "alice@example.com", "2024-01-01"),
            ContractTests.ApiUser(0, "invalid", "bad-email", "")
        )
        
        // Act
        val result = ContractTests.validateUserList(users)
        
        // Assert
        assertTrue("Invalid user should fail list validation", result is ContractTests.ValidationResult.Invalid)
        val errors = (result as ContractTests.ValidationResult.Invalid).errors
        assertTrue("Should have multiple errors", errors.size > 1)
    }
    
    /**
     * Test contract test types are properly defined.
     */
    @Test
    fun `contract test types are properly defined`() {
        // Act
        val types = ContractTests.getContractTestTypes()
        
        // Assert
        assertTrue("Should have at least 8 test types", types.size >= 8)
        
        val descriptions = types.map { it.description }
        assertTrue("Should include schema validation",
            descriptions.any { it.contains("schema") || it.contains("structure") })
        assertTrue("Should include status code validation",
            descriptions.any { it.contains("status") })
        assertTrue("Should include backward compatibility",
            descriptions.any { it.contains("backward") || it.contains("compatibility") })
    }
    
    /**
     * Integration test: Full contract validation flow.
     */
    @Test
    fun `full contract validation flow works end-to-end`() = runBlocking {
        // Arrange: Complete valid response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("""{"id":100,"username":"endtoend","email":"e2e@example.com","createdAt":"2024-12-01"}""")
        )
        
        // Act: Fetch and validate
        val response = service.getUser(100)
        val contractResult = ContractTests.validateSuccessContract(response)
        val user = response.body()!!
        val schemaResult = ContractTests.validateUserSchema(user)
        
        // Assert: All validations pass
        assertTrue("Contract should be valid", contractResult is ContractTests.ValidationResult.Valid)
        assertTrue("Schema should be valid", schemaResult is ContractTests.ValidationResult.Valid)
        assertEquals(100, user.id)
        assertEquals("endtoend", user.username)
    }
}

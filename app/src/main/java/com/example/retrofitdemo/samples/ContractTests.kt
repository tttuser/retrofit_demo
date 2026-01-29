package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * ContractTests: Demonstrates contract testing patterns for API validation.
 * 
 * Learning nodes:
 * - L4-4: API contract testing
 * - L4-5: Schema validation
 * 
 * This sample demonstrates:
 * 1. Validating API responses match expected contract
 * 2. Schema validation (field presence, types)
 * 3. Testing response structure consistency
 * 4. Verifying headers and status codes
 * 5. Ensuring backward compatibility
 * 
 * Source reading notes:
 * - Contract tests verify API behavior matches specification
 * - Different from unit tests (test actual API, not mocks)
 * - Catches breaking changes in API responses
 * - Validates both happy path and error scenarios
 * - Can use real API or contract testing tools (Pact, Spring Cloud Contract)
 */
object ContractTests {
    
    /**
     * Data models representing expected API contract.
     */
    data class ApiUser(
        val id: Int,
        val username: String,
        val email: String,
        val createdAt: String
    )
    
    data class ApiError(
        val code: String,
        val message: String,
        val details: String? = null
    )
    
    /**
     * Service interface for contract testing.
     */
    interface ContractService {
        @GET("users/{id}")
        suspend fun getUser(@Path("id") id: Int): Response<ApiUser>
        
        @GET("users")
        suspend fun getUsers(): Response<List<ApiUser>>
    }
    
    /**
     * Creates a Retrofit instance for ContractService.
     * 
     * @param baseUrl The base URL for the API
     * @return Configured Retrofit instance
     */
    fun createRetrofit(baseUrl: String): Retrofit {
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Creates the ContractService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return ContractService implementation
     */
    fun createService(retrofit: Retrofit): ContractService {
        return retrofit.create(ContractService::class.java)
    }
    
    /**
     * Contract validation result.
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }
    
    /**
     * Validates that a successful response matches the contract.
     * 
     * Checks:
     * - Status code is 2xx
     * - Content-Type is application/json
     * - Response body is present
     * - Required fields are present
     */
    fun <T> validateSuccessContract(response: Response<T>): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check status code
        if (!response.isSuccessful) {
            errors.add("Expected 2xx status code, got ${response.code()}")
        }
        
        // Check Content-Type header
        val contentType = response.headers()["Content-Type"]
        if (contentType == null || !contentType.contains("application/json")) {
            errors.add("Expected Content-Type: application/json, got $contentType")
        }
        
        // Check body is present
        if (response.body() == null) {
            errors.add("Response body is null")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validates that an error response matches the contract.
     * 
     * Checks:
     * - Status code is 4xx or 5xx
     * - Error body is present
     * - Error body can be parsed
     */
    fun validateErrorContract(response: Response<*>): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check status code
        if (response.isSuccessful) {
            errors.add("Expected non-2xx status code, got ${response.code()}")
        }
        
        // Check error body is present
        val errorBody = response.errorBody()?.string()
        if (errorBody == null || errorBody.isEmpty()) {
            errors.add("Error body is missing or empty")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validates that a User object has all required fields.
     */
    fun validateUserSchema(user: ApiUser): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check ID is positive
        if (user.id <= 0) {
            errors.add("User ID must be positive, got ${user.id}")
        }
        
        // Check username is not empty
        if (user.username.isEmpty()) {
            errors.add("Username cannot be empty")
        }
        
        // Check email format (basic validation)
        if (!user.email.contains("@")) {
            errors.add("Email must contain @, got ${user.email}")
        }
        
        // Check createdAt is not empty
        if (user.createdAt.isEmpty()) {
            errors.add("CreatedAt cannot be empty")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validates that a list of users is valid.
     */
    fun validateUserList(users: List<ApiUser>): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check list is not empty (if expecting data)
        if (users.isEmpty()) {
            errors.add("User list is empty")
        }
        
        // Validate each user
        users.forEachIndexed { index, user ->
            when (val result = validateUserSchema(user)) {
                is ValidationResult.Invalid -> {
                    result.errors.forEach { error ->
                        errors.add("User[$index]: $error")
                    }
                }
                ValidationResult.Valid -> { /* OK */ }
            }
        }
        
        // Check IDs are unique
        val ids = users.map { it.id }
        val uniqueIds = ids.toSet()
        if (ids.size != uniqueIds.size) {
            errors.add("User IDs are not unique")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Contract test types.
     */
    enum class ContractTestType(val description: String) {
        SCHEMA_VALIDATION("Validate response structure and field types"),
        STATUS_CODE_VALIDATION("Verify correct HTTP status codes"),
        HEADER_VALIDATION("Check required response headers"),
        ERROR_FORMAT_VALIDATION("Ensure consistent error response format"),
        BACKWARD_COMPATIBILITY("Verify new versions don't break old clients"),
        RESPONSE_TIME_SLA("Check response time meets SLA"),
        PAGINATION_CONTRACT("Validate pagination metadata"),
        VERSIONING_CONTRACT("Ensure API versioning works correctly")
    }
    
    /**
     * Gets all contract test types.
     */
    fun getContractTestTypes(): List<ContractTestType> {
        return ContractTestType.values().toList()
    }
}

package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.io.IOException
import com.squareup.moshi.JsonDataException

/**
 * FailureMatrixSampleTest: Demonstrates different types of API failures.
 * 
 * Learning nodes:
 * - L1-4: Different failure scenarios and their characteristics
 * - L4-7: Advanced error classification and handling strategies
 * 
 * This test demonstrates:
 * 1. HTTP 404 Not Found - Client error
 * 2. Network failure - Connection refused, timeout, etc.
 * 3. Malformed JSON - Parse error from invalid response
 * 4. How to classify and handle each error type
 * 
 * Source reading notes:
 * - HttpException: HTTP error responses (4xx, 5xx)
 * - IOException: Network failures (no connection, timeout)
 * - JsonDataException: JSON parsing failures
 * - Each error type requires different handling strategy
 */
class FailureMatrixSampleTest {
    
    /**
     * Simple data model for testing.
     */
    data class TestData(
        val id: Int,
        val value: String
    )
    
    /**
     * Service interface for failure testing.
     */
    interface FailureService {
        @GET("data")
        suspend fun getData(): TestData
    }
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: FailureService
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        service = retrofit.create(FailureService::class.java)
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test HTTP 404 error produces HttpException.
     * 
     * Classification: Client Error (4xx)
     * Handling: Check if resource exists, show appropriate message to user
     */
    @Test
    fun `404 Not Found produces HttpException with code 404`() = runBlocking {
        // Arrange: Mock 404 response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"Resource not found"}""")
        )
        
        // Act & Assert
        try {
            service.getData()
            fail("Should have thrown HttpException")
        } catch (e: HttpException) {
            // Verify it's an HTTP error
            assertEquals("Should be 404 error", 404, e.code())
            
            // Verify error body is accessible
            val errorBody = e.response()?.errorBody()?.string()
            assertTrue(
                "Error body should contain message",
                errorBody?.contains("Resource not found") == true
            )
            
            // Classification
            assertTrue("Should be client error (4xx)", e.code() in 400..499)
        } catch (e: Exception) {
            fail("Should throw HttpException, not ${e.javaClass.simpleName}")
        }
    }
    
    /**
     * Test HTTP 500 error produces HttpException.
     * 
     * Classification: Server Error (5xx)
     * Handling: Retry with exponential backoff, show "try again later" message
     */
    @Test
    fun `500 Internal Server Error produces HttpException with code 500`() = runBlocking {
        // Arrange: Mock 500 response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"Internal server error"}""")
        )
        
        // Act & Assert
        try {
            service.getData()
            fail("Should have thrown HttpException")
        } catch (e: HttpException) {
            // Verify it's a server error
            assertEquals("Should be 500 error", 500, e.code())
            
            // Classification
            assertTrue("Should be server error (5xx)", e.code() in 500..599)
        } catch (e: Exception) {
            fail("Should throw HttpException, not ${e.javaClass.simpleName}")
        }
    }
    
    /**
     * Test network failure produces IOException.
     * 
     * Classification: Network Error
     * Handling: Check network connectivity, retry, show "check connection" message
     */
    @Test
    fun `network failure produces IOException`() = runBlocking {
        // Arrange: Shutdown server to simulate network failure
        mockWebServer.shutdown()
        
        // Act & Assert
        try {
            service.getData()
            fail("Should have thrown IOException")
        } catch (e: IOException) {
            // Verify it's a network error
            assertNotNull("IOException should not be null", e)
            assertTrue(
                "Message should indicate connection issue",
                e.message?.contains("Connection refused") == true ||
                e.message?.contains("Failed to connect") == true
            )
        } catch (e: HttpException) {
            fail("Should throw IOException, not HttpException")
        }
    }
    
    /**
     * Test malformed JSON produces JsonDataException.
     * 
     * Classification: Parse Error
     * Handling: Log error, report to backend team, show generic error message
     */
    @Test
    fun `malformed JSON produces JsonDataException`() = runBlocking {
        // Arrange: Return invalid JSON
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":""") // Missing closing quote and brace
        )
        
        // Act & Assert
        try {
            service.getData()
            fail("Should have thrown JsonDataException")
        } catch (e: JsonDataException) {
            // Verify it's a JSON parsing error
            assertNotNull("JsonDataException should not be null", e)
            assertTrue(
                "Message should indicate parsing issue",
                e.message != null
            )
        } catch (e: Exception) {
            fail("Should throw JsonDataException, not ${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    /**
     * Test missing required field produces JsonDataException.
     */
    @Test
    fun `missing required field produces JsonDataException`() = runBlocking {
        // Arrange: Return JSON missing required field
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1}""") // Missing 'value' field
        )
        
        // Act & Assert
        try {
            service.getData()
            fail("Should have thrown JsonDataException")
        } catch (e: JsonDataException) {
            // Verify it's a JSON parsing error for missing field
            assertNotNull("JsonDataException should not be null", e)
        } catch (e: Exception) {
            fail("Should throw JsonDataException, not ${e.javaClass.simpleName}")
        }
    }
    
    /**
     * Test wrong data type produces JsonDataException.
     */
    @Test
    fun `wrong data type produces JsonDataException`() = runBlocking {
        // Arrange: Return JSON with wrong data type
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"not-a-number","value":"test"}""") // id should be Int
        )
        
        // Act & Assert
        try {
            service.getData()
            fail("Should have thrown JsonDataException")
        } catch (e: JsonDataException) {
            // Verify it's a JSON parsing error for type mismatch
            assertNotNull("JsonDataException should not be null", e)
        } catch (e: Exception) {
            fail("Should throw JsonDataException, not ${e.javaClass.simpleName}")
        }
    }
    
    /**
     * Comprehensive test demonstrating error classification strategy.
     */
    @Test
    fun `error classification by exception type`() = runBlocking {
        // Test each error type and classify
        val errorTypes = mutableListOf<String>()
        
        // Test 1: HTTP error
        mockWebServer.enqueue(MockResponse().setResponseCode(400))
        try {
            service.getData()
        } catch (e: HttpException) {
            errorTypes.add("HTTP_ERROR")
        }
        
        // Test 2: Network error
        mockWebServer.shutdown()
        try {
            service.getData()
        } catch (e: IOException) {
            if (e !is HttpException) {
                errorTypes.add("NETWORK_ERROR")
            }
        }
        
        // Test 3: Parse error
        mockWebServer.start()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""invalid json""")
        )
        try {
            service.getData()
        } catch (e: JsonDataException) {
            errorTypes.add("PARSE_ERROR")
        }
        
        mockWebServer.shutdown()
        
        // Assert: All three error types were classified
        assertTrue("Should detect HTTP error", errorTypes.contains("HTTP_ERROR"))
        assertTrue("Should detect network error", errorTypes.contains("NETWORK_ERROR"))
        assertTrue("Should detect parse error", errorTypes.contains("PARSE_ERROR"))
        assertEquals("Should have 3 distinct error types", 3, errorTypes.size)
    }
}

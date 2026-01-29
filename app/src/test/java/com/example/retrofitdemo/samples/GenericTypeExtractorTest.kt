package com.example.retrofitdemo.samples

import org.junit.Assert.*
import org.junit.Test
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import java.lang.reflect.Method

/**
 * GenericTypeExtractorTest: Tests for GenericTypeExtractor sample.
 * 
 * Test strategy:
 * 1. Test extraction of simple generic types (Call<T>, List<T>)
 * 2. Test nested generic types (Call<List<T>>)
 * 3. Verify type checking helpers
 * 4. Test method signature analysis
 */
class GenericTypeExtractorTest {
    
    /**
     * Test service interface for extracting method signatures.
     */
    interface TestService {
        @GET("user")
        fun getUser(): Call<GenericTypeExtractor.User>
        
        @GET("users")
        fun getUsers(): Call<List<GenericTypeExtractor.User>>
        
        @GET("post")
        suspend fun getPost(): Response<GenericTypeExtractor.Post>
        
        @GET("posts")
        suspend fun getPosts(): List<GenericTypeExtractor.Post>
    }
    
    /**
     * Test extracting generic type from Call<User>.
     */
    @Test
    fun `extract generic type from Call`() {
        // Arrange: Get method return type
        val method = TestService::class.java.getMethod("getUser")
        val returnType = method.genericReturnType
        
        // Act: Extract generic type
        val innerType = GenericTypeExtractor.extractGenericType(returnType)
        
        // Assert: Should extract User type
        assertNotNull("Should extract inner type", innerType)
        assertTrue(
            "Inner type should be User",
            innerType.toString().contains("User")
        )
    }
    
    /**
     * Test extracting nested generic types from Call<List<User>>.
     */
    @Test
    fun `extract nested generic types from Call of List`() {
        // Arrange: Get method return type
        val method = TestService::class.java.getMethod("getUsers")
        val returnType = method.genericReturnType
        
        // Act: Extract nested types
        val nestedTypes = GenericTypeExtractor.extractNestedGenericTypes(returnType)
        
        // Assert: Should extract [List<User>, User]
        assertTrue("Should extract at least 2 levels", nestedTypes.size >= 2)
        assertTrue(
            "First level should be List",
            nestedTypes[0].toString().contains("List")
        )
        assertTrue(
            "Second level should be User",
            nestedTypes[1].toString().contains("User")
        )
    }
    
    /**
     * Test Call type detection.
     */
    @Test
    fun `isCallType detects Call types`() {
        // Arrange
        val callMethod = TestService::class.java.getMethod("getUser")
        val suspendMethod = TestService::class.java.getMethod("getPosts")
        
        // Act & Assert
        assertTrue(
            "Call<User> should be detected as Call type",
            GenericTypeExtractor.isCallType(callMethod.genericReturnType)
        )
        assertFalse(
            "List<Post> should not be detected as Call type",
            GenericTypeExtractor.isCallType(suspendMethod.genericReturnType)
        )
    }
    
    /**
     * Test Response type detection.
     */
    @Test
    fun `isResponseType detects Response types`() {
        // Arrange
        val responseMethod = TestService::class.java.getMethod("getPost")
        val callMethod = TestService::class.java.getMethod("getUser")
        
        // Act & Assert
        assertTrue(
            "Response<Post> should be detected as Response type",
            GenericTypeExtractor.isResponseType(responseMethod.genericReturnType)
        )
        assertFalse(
            "Call<User> should not be detected as Response type",
            GenericTypeExtractor.isResponseType(callMethod.genericReturnType)
        )
    }
    
    /**
     * Test List type detection.
     */
    @Test
    fun `isListType detects List types`() {
        // Arrange
        val listMethod = TestService::class.java.getMethod("getPosts")
        val callMethod = TestService::class.java.getMethod("getUser")
        
        // Act & Assert
        assertTrue(
            "List<Post> should be detected as List type",
            GenericTypeExtractor.isListType(listMethod.genericReturnType)
        )
        assertFalse(
            "Call<User> should not be detected as List type",
            GenericTypeExtractor.isListType(callMethod.genericReturnType)
        )
    }
    
    /**
     * Test type description generation.
     */
    @Test
    fun `getTypeDescription generates readable descriptions`() {
        // Arrange
        val userMethod = TestService::class.java.getMethod("getUser")
        val usersMethod = TestService::class.java.getMethod("getUsers")
        
        // Act
        val userDesc = GenericTypeExtractor.getTypeDescription(userMethod.genericReturnType)
        val usersDesc = GenericTypeExtractor.getTypeDescription(usersMethod.genericReturnType)
        
        // Assert
        assertTrue("Should describe Call<User>", userDesc.contains("Call"))
        assertTrue("Should describe Call<User>", userDesc.contains("User"))
        assertTrue("Should describe Call<List<User>>", usersDesc.contains("Call"))
        assertTrue("Should describe Call<List<User>>", usersDesc.contains("List"))
        assertTrue("Should describe Call<List<User>>", usersDesc.contains("User"))
    }
    
    /**
     * Test raw class extraction.
     */
    @Test
    fun `getRawClass extracts raw class from generic type`() {
        // Arrange
        val callMethod = TestService::class.java.getMethod("getUser")
        val listMethod = TestService::class.java.getMethod("getPosts")
        
        // Act
        val callClass = GenericTypeExtractor.getRawClass(callMethod.genericReturnType)
        val listClass = GenericTypeExtractor.getRawClass(listMethod.genericReturnType)
        
        // Assert
        assertEquals("Should extract Call class", Call::class.java, callClass)
        assertEquals("Should extract List class", List::class.java, listClass)
    }
    
    /**
     * Test method analysis for Call<T>.
     */
    @Test
    fun `analyzeRetrofitMethod analyzes Call return type`() {
        // Arrange
        val method = TestService::class.java.getMethod("getUser")
        
        // Act
        val analysis = GenericTypeExtractor.analyzeRetrofitMethod(method.genericReturnType)
        
        // Assert
        assertTrue("Should detect Call usage", analysis.usesCall)
        assertTrue("Return type should contain Call", analysis.returnType.contains("Call"))
        assertTrue("Inner type should contain User", analysis.innerType?.contains("User") == true)
        assertEquals("Raw class should be Call", "Call", analysis.rawClass)
    }
    
    /**
     * Test method analysis for List<T>.
     */
    @Test
    fun `analyzeRetrofitMethod analyzes List return type`() {
        // Arrange
        val method = TestService::class.java.getMethod("getPosts")
        
        // Act
        val analysis = GenericTypeExtractor.analyzeRetrofitMethod(method.genericReturnType)
        
        // Assert
        assertFalse("Should not detect Call usage", analysis.usesCall)
        assertTrue("Return type should contain List", analysis.returnType.contains("List"))
        assertTrue("Inner type should contain Post", analysis.innerType?.contains("Post") == true)
        assertEquals("Raw class should be List", "List", analysis.rawClass)
    }
}

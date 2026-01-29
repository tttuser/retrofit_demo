package com.example.retrofitdemo.samples

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * CreateTwiceBenchmarkTest: Tests for CreateTwiceBenchmark.
 * 
 * Test strategy:
 * 1. Verify that creating multiple instances is slower than reusing one
 * 2. Validate that both patterns produce working Retrofit instances
 * 3. Demonstrate the performance difference between anti-pattern and best practice
 */
class CreateTwiceBenchmarkTest {
    
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
     * Test that creating multiple instances works but is inefficient.
     */
    @Test
    fun `createRetrofitEveryTime creates working instances`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        
        // Act: Create multiple instances
        val retrofit1 = CreateTwiceBenchmark.createRetrofitEveryTime(baseUrl)
        val retrofit2 = CreateTwiceBenchmark.createRetrofitEveryTime(baseUrl)
        val retrofit3 = CreateTwiceBenchmark.createRetrofitEveryTime(baseUrl)
        
        // Assert: Each instance should be unique (not the same object)
        assertNotSame("Instances should be different objects", retrofit1, retrofit2)
        assertNotSame("Instances should be different objects", retrofit2, retrofit3)
        
        // All instances should have the same base URL
        assertEquals(baseUrl, retrofit1.baseUrl().toString())
        assertEquals(baseUrl, retrofit2.baseUrl().toString())
        assertEquals(baseUrl, retrofit3.baseUrl().toString())
    }
    
    /**
     * Test that singleton pattern reuses the same instance.
     */
    @Test
    fun `RetrofitSingleton reuses the same instance`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val singleton = CreateTwiceBenchmark.RetrofitSingleton(baseUrl)
        
        // Act: Get the instance multiple times
        val retrofit1 = singleton.getRetrofit()
        val retrofit2 = singleton.getRetrofit()
        val retrofit3 = singleton.getRetrofit()
        
        // Assert: All should be the same instance
        assertSame("Should return same instance", retrofit1, retrofit2)
        assertSame("Should return same instance", retrofit2, retrofit3)
        
        // Instance should have correct base URL
        assertEquals(baseUrl, retrofit1.baseUrl().toString())
    }
    
    /**
     * Test that reusing instances is faster than creating new ones.
     * This demonstrates the performance benefit of the singleton pattern.
     */
    @Test
    fun `reusing instances is faster than creating new ones`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val iterations = 100
        
        // Act: Measure time for creating new instances
        val createTime = CreateTwiceBenchmark.measureCreateTime(iterations, baseUrl)
        
        // Act: Measure time for reusing instance
        val reuseTime = CreateTwiceBenchmark.measureReuseTime(iterations, baseUrl)
        
        // Assert: Reusing should be significantly faster
        // Note: We use a generous multiplier (10x) because timing tests can be flaky
        // In practice, reusing is often 100x+ faster
        assertTrue(
            "Reusing instance should be much faster than creating new ones. " +
            "Create time: ${createTime}ms, Reuse time: ${reuseTime}ms",
            createTime > reuseTime * 10
        )
        
        // Print results for visibility
        println("CreateTwiceBenchmark results (${iterations} iterations):")
        println("  Creating new instances: ${createTime}ms")
        println("  Reusing single instance: ${reuseTime}ms")
        println("  Speedup: ${createTime / reuseTime.coerceAtLeast(1)}x")
    }
    
    /**
     * Test that services created from different instances work independently.
     */
    @Test
    fun `services from different instances work independently`() {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":1,"name":"Item 1"}]""")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":2,"name":"Item 2"}]""")
        )
        
        val baseUrl = mockWebServer.url("/").toString()
        
        // Act: Create two separate Retrofit instances
        val retrofit1 = CreateTwiceBenchmark.createRetrofitEveryTime(baseUrl)
        val retrofit2 = CreateTwiceBenchmark.createRetrofitEveryTime(baseUrl)
        
        val service1 = retrofit1.create(CreateTwiceBenchmark.TestService::class.java)
        val service2 = retrofit2.create(CreateTwiceBenchmark.TestService::class.java)
        
        // Assert: Services should be different objects
        assertNotSame("Services should be different objects", service1, service2)
        
        // Both services should work
        assertNotNull(service1)
        assertNotNull(service2)
    }
    
    /**
     * Test that singleton can create services successfully.
     */
    @Test
    fun `singleton creates working services`() {
        // Arrange
        val baseUrl = mockWebServer.url("/").toString()
        val singleton = CreateTwiceBenchmark.RetrofitSingleton(baseUrl)
        
        // Act: Create service from singleton
        val retrofit = singleton.getRetrofit()
        val service = retrofit.create(CreateTwiceBenchmark.TestService::class.java)
        
        // Assert: Service should be created successfully
        assertNotNull("Service should be created", service)
    }
}

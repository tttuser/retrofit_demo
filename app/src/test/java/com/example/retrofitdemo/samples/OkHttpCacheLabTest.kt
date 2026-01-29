package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * OkHttpCacheLabTest: Tests for OkHttpCacheLab sample.
 * 
 * Test strategy:
 * 1. Test cache hit when Cache-Control header allows caching
 * 2. Test cache miss when no cache configured
 * 3. Test cache respects max-age directive
 * 4. Verify cache statistics tracking
 * 5. Test cache eviction and clearing
 */
class OkHttpCacheLabTest {
    
    @get:Rule
    val tempFolder = TemporaryFolder()
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var cacheDir: File
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Create temporary cache directory
        cacheDir = tempFolder.newFolder("http-cache")
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that cacheable response is cached.
     */
    @Test
    fun `cacheable response is stored in cache`() = runBlocking {
        // Arrange: Mock response with Cache-Control header
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "max-age=60")
                .setBody("""{"value":"cached","timestamp":1000}""")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"value":"should not be called","timestamp":2000}""")
        )
        
        val retrofit = OkHttpCacheLab.createWithCache(cacheDir)
        val service = retrofit.create(OkHttpCacheLab.CacheTestService::class.java)
        
        // Act: Make first request (cache miss)
        val response1 = service.getCacheableData()
        val analysis1 = OkHttpCacheLab.analyzeCacheUsage(response1)
        
        // Act: Make second request (should be cache hit)
        val response2 = service.getCacheableData()
        val analysis2 = OkHttpCacheLab.analyzeCacheUsage(response2)
        
        // Assert: First request should hit network
        assertTrue(
            "First request should come from network",
            analysis1["fromNetwork"] as Boolean
        )
        
        // Assert: Second request should come from cache
        assertTrue(
            "Second request should come from cache",
            analysis2["fromCache"] as Boolean
        )
        
        // Only one network request should have been made
        assertEquals("Only one network request should be made", 1, mockWebServer.requestCount)
    }
    
    /**
     * Test that without cache, all requests hit network.
     */
    @Test
    fun `without cache all requests hit network`() = runBlocking {
        // Arrange: Mock multiple responses
        repeat(3) { i ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Cache-Control", "max-age=60")
                    .setBody("""{"value":"data-$i","timestamp":${1000 + i}}""")
            )
        }
        
        val retrofit = OkHttpCacheLab.createWithoutCache()
        val service = retrofit.create(OkHttpCacheLab.CacheTestService::class.java)
        
        // Act: Make multiple requests
        service.getCacheableData()
        service.getCacheableData()
        service.getCacheableData()
        
        // Assert: All requests should hit network
        assertEquals("All requests should hit network", 3, mockWebServer.requestCount)
    }
    
    /**
     * Test that Cache-Control: no-cache prevents caching.
     */
    @Test
    fun `no-cache directive prevents caching`() = runBlocking {
        // Arrange: Mock response with no-cache
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "no-cache")
                .setBody("""{"value":"not-cached-1","timestamp":1000}""")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "no-cache")
                .setBody("""{"value":"not-cached-2","timestamp":2000}""")
        )
        
        val retrofit = OkHttpCacheLab.createWithCache(cacheDir)
        val service = retrofit.create(OkHttpCacheLab.CacheTestService::class.java)
        
        // Act: Make two requests
        val response1 = service.getNoCacheData()
        val response2 = service.getNoCacheData()
        
        // Assert: Both should hit network
        val analysis1 = OkHttpCacheLab.analyzeCacheUsage(response1)
        val analysis2 = OkHttpCacheLab.analyzeCacheUsage(response2)
        
        assertTrue("First request from network", analysis1["fromNetwork"] as Boolean)
        // With no-cache, it should still validate with server
        assertTrue("Second request from network", analysis2["fromNetwork"] as Boolean)
        
        // Both requests should hit the network
        assertEquals("Both requests should hit network", 2, mockWebServer.requestCount)
    }
    
    /**
     * Test cache statistics tracking.
     */
    @Test
    fun `cache statistics are tracked correctly`() = runBlocking {
        // Arrange: Mock cacheable responses
        repeat(5) { i ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Cache-Control", "max-age=60")
                    .setBody("""{"value":"data-$i","timestamp":${1000 + i}}""")
            )
        }
        
        val retrofit = OkHttpCacheLab.createWithCache(cacheDir, cacheSizeBytes = 1024 * 1024)
        val service = retrofit.create(OkHttpCacheLab.CacheTestService::class.java)
        val client = retrofit.callFactory() as OkHttpClient
        
        // Act: Make requests
        service.getCacheableData() // Network request
        service.getCacheableData() // Cache hit
        service.getCacheableData() // Cache hit
        
        // Get cache stats
        val stats = OkHttpCacheLab.getCacheStats(client)
        
        // Assert: Stats should reflect usage
        assertEquals("Request count should be 3", 3L, stats["requestCount"])
        assertEquals("Network count should be 1", 1L, stats["networkCount"])
        assertEquals("Hit count should be 2", 2L, stats["hitCount"])
        assertEquals("Max size should be 1MB", 1024L * 1024L, stats["maxSizeBytes"])
        assertTrue("Current size should be > 0", (stats["currentSizeBytes"] ?: 0L) > 0)
    }
    
    /**
     * Test cache can be cleared.
     */
    @Test
    fun `cache can be cleared`() = runBlocking {
        // Arrange: Mock cacheable response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "max-age=60")
                .setBody("""{"value":"cached","timestamp":1000}""")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "max-age=60")
                .setBody("""{"value":"after-clear","timestamp":2000}""")
        )
        
        val retrofit = OkHttpCacheLab.createWithCache(cacheDir)
        val service = retrofit.create(OkHttpCacheLab.CacheTestService::class.java)
        val client = retrofit.callFactory() as OkHttpClient
        
        // Act: Cache a response
        service.getCacheableData()
        
        val statsBeforeClear = OkHttpCacheLab.getCacheStats(client)
        assertTrue("Cache should have data", (statsBeforeClear["currentSizeBytes"] ?: 0L) > 0)
        
        // Clear cache
        OkHttpCacheLab.clearCache(client)
        
        val statsAfterClear = OkHttpCacheLab.getCacheStats(client)
        
        // Make another request (should hit network again)
        service.getCacheableData()
        
        // Assert: Cache was cleared
        assertEquals("Cache size should be 0 after clear", 0L, statsAfterClear["currentSizeBytes"])
        assertEquals("Should have made 2 network requests", 2, mockWebServer.requestCount)
    }
    
    /**
     * Test cache analysis for network response.
     */
    @Test
    fun `cache analysis detects network response`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "max-age=60")
                .setBody("""{"value":"network","timestamp":1000}""")
        )
        
        val retrofit = OkHttpCacheLab.createWithCache(cacheDir)
        val service = retrofit.create(OkHttpCacheLab.CacheTestService::class.java)
        
        // Act: First request (network)
        val response = service.getCacheableData()
        val analysis = OkHttpCacheLab.analyzeCacheUsage(response)
        
        // Assert: Should be from network
        assertTrue("Should be from network", analysis["fromNetwork"] as Boolean)
        assertFalse("Should not be from cache", analysis["fromCache"] as Boolean)
        assertFalse("Should not be conditional hit", analysis["conditionalHit"] as Boolean)
    }
    
    /**
     * Test cache analysis for cached response.
     */
    @Test
    fun `cache analysis detects cached response`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "max-age=60")
                .setBody("""{"value":"cached","timestamp":1000}""")
        )
        
        val retrofit = OkHttpCacheLab.createWithCache(cacheDir)
        val service = retrofit.create(OkHttpCacheLab.CacheTestService::class.java)
        
        // Act: First request (network), second request (cache)
        service.getCacheableData()
        val response = service.getCacheableData()
        val analysis = OkHttpCacheLab.analyzeCacheUsage(response)
        
        // Assert: Should be from cache
        assertTrue("Should be from cache", analysis["fromCache"] as Boolean)
        assertFalse("Should not be from network", analysis["fromNetwork"] as Boolean)
    }
    
    /**
     * Test that cache stats return empty map when no cache configured.
     */
    @Test
    fun `cache stats return empty when no cache configured`() {
        // Arrange
        val retrofit = OkHttpCacheLab.createWithoutCache()
        val client = retrofit.callFactory() as OkHttpClient
        
        // Act
        val stats = OkHttpCacheLab.getCacheStats(client)
        
        // Assert: Should return empty map
        assertTrue("Stats should be empty without cache", stats.isEmpty())
    }
}

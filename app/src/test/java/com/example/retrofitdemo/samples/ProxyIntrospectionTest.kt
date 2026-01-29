package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * ProxyIntrospectionTest: Tests for ProxyIntrospection sample.
 * 
 * Test strategy:
 * 1. Verify Retrofit creates proxy instances
 * 2. Check proxy interfaces and methods
 * 3. Validate proxy class characteristics
 * 4. Test introspection utilities
 */
class ProxyIntrospectionTest {
    
    private lateinit var mockWebServer: MockWebServer
    private lateinit var retrofit: retrofit2.Retrofit
    
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        retrofit = ProxyIntrospection.createRetrofit(mockWebServer.url("/").toString())
    }
    
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    /**
     * Test that Retrofit creates proxy instances.
     */
    @Test
    fun `service is a dynamic proxy`() {
        val service = retrofit.create(ProxyIntrospection.ProxyTestService::class.java)
        
        // Assert: Service should be a proxy
        assertTrue("Service should be a proxy", ProxyIntrospection.isProxy(service))
        assertTrue("Service class should be a proxy class", Proxy.isProxyClass(service.javaClass))
    }
    
    /**
     * Test that proxy implements the service interface.
     */
    @Test
    fun `proxy implements service interface`() {
        val service = retrofit.create(ProxyIntrospection.ProxyTestService::class.java)
        val interfaces = ProxyIntrospection.getProxyInterfaces(service)
        
        // Assert: Should implement ProxyTestService
        assertTrue(
            "Should implement ProxyTestService",
            interfaces.any { it.name.contains("ProxyTestService") }
        )
    }
    
    /**
     * Test introspection returns correct information.
     */
    @Test
    fun `introspection returns complete service information`() {
        val service = retrofit.create(ProxyIntrospection.ProxyTestService::class.java)
        val info = ProxyIntrospection.introspectService(service)
        
        // Assert: Info should contain expected keys
        assertTrue("Should be a proxy", info["isProxy"] as Boolean)
        assertNotNull("Should have class name", info["className"])
        
        val interfaces = info["interfaces"] as List<*>
        assertTrue(
            "Should have ProxyTestService interface",
            interfaces.any { it.toString().contains("ProxyTestService") }
        )
        
        val methods = info["methods"] as List<*>
        assertTrue("Should have getItem method", methods.contains("getItem"))
        assertTrue("Should have listItems method", methods.contains("listItems"))
    }
    
    /**
     * Test that multiple service instances share the same proxy class.
     */
    @Test
    fun `multiple service instances share same proxy class`() {
        val same = ProxyIntrospection.sameProxyClass(retrofit)
        
        // Assert: Should use the same proxy class
        assertTrue(
            "Multiple service instances should share the same proxy class",
            same
        )
    }
    
    /**
     * Test that proxy methods can be called successfully.
     */
    @Test
    fun `proxy methods can be invoked`() = runBlocking {
        // Arrange: Mock response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"name":"Test Item"}""")
        )
        
        val service = retrofit.create(ProxyIntrospection.ProxyTestService::class.java)
        
        // Act: Call method through proxy
        val item = service.getItem(1)
        
        // Assert: Method call should work
        assertEquals(1, item.id)
        assertEquals("Test Item", item.name)
        
        val request = mockWebServer.takeRequest()
        assertTrue("Should call correct path", request.path?.contains("/items/1") == true)
    }
    
    /**
     * Test that proxy class name indicates it's a proxy.
     */
    @Test
    fun `proxy class name indicates proxy nature`() {
        val service = retrofit.create(ProxyIntrospection.ProxyTestService::class.java)
        val className = service.javaClass.name
        
        // Assert: Class name should indicate proxy
        // JDK proxies typically have names like "$Proxy0", "$Proxy1", etc.
        assertTrue(
            "Class name should indicate proxy: $className",
            className.contains("Proxy") || className.startsWith("$")
        )
    }
    
    /**
     * Test introspection of methods.
     */
    @Test
    fun `can introspect all service methods`() {
        val service = retrofit.create(ProxyIntrospection.ProxyTestService::class.java)
        val info = ProxyIntrospection.introspectService(service)
        val methods = info["methods"] as List<*>
        
        // Assert: Should have both declared methods
        assertEquals(
            "Should have exactly 2 methods",
            2,
            methods.size
        )
        assertTrue("Should have getItem", methods.contains("getItem"))
        assertTrue("Should have listItems", methods.contains("listItems"))
    }
    
    /**
     * Test that regular objects are not proxies.
     */
    @Test
    fun `regular objects are not proxies`() {
        val regularObject = Any()
        
        // Assert: Regular object should not be a proxy
        assertFalse("Regular object should not be a proxy", ProxyIntrospection.isProxy(regularObject))
    }
}

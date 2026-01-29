package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * BaseUrlSwitchTest: Tests for BaseUrlSwitch sample.
 * 
 * Test strategy:
 * 1. Test creating Retrofit for different environments
 * 2. Test dynamic base URL switching with interceptor
 * 3. Test EnvironmentManager for managing multiple environments
 * 4. Verify requests go to correct URLs
 * 5. Test environment switching behavior
 */
class BaseUrlSwitchTest {
    
    private lateinit var devServer: MockWebServer
    private lateinit var stagingServer: MockWebServer
    private lateinit var prodServer: MockWebServer
    
    @Before
    fun setup() {
        // Create separate mock servers for each environment
        devServer = MockWebServer()
        stagingServer = MockWebServer()
        prodServer = MockWebServer()
        
        devServer.start()
        stagingServer.start()
        prodServer.start()
    }
    
    @After
    fun tearDown() {
        devServer.shutdown()
        stagingServer.shutdown()
        prodServer.shutdown()
    }
    
    /**
     * Test creating Retrofit for specific environment.
     */
    @Test
    fun `createForEnvironment uses correct base URL`() {
        // Arrange: Create custom environments with mock server URLs
        val devEnv = BaseUrlSwitch.Environment.DEVELOPMENT
        
        // Act: Create Retrofit instance
        val retrofit = BaseUrlSwitch.createForEnvironment(devEnv)
        
        // Assert: Base URL should match environment
        assertEquals(
            devEnv.baseUrl,
            retrofit.baseUrl().toString()
        )
    }
    
    /**
     * Test that different environments have different base URLs.
     */
    @Test
    fun `different environments have different base URLs`() {
        // Act: Create instances for each environment
        val devRetrofit = BaseUrlSwitch.createForEnvironment(BaseUrlSwitch.Environment.DEVELOPMENT)
        val stagingRetrofit = BaseUrlSwitch.createForEnvironment(BaseUrlSwitch.Environment.STAGING)
        val prodRetrofit = BaseUrlSwitch.createForEnvironment(BaseUrlSwitch.Environment.PRODUCTION)
        
        // Assert: Each should have correct base URL
        assertEquals(
            "https://dev-api.example.com/",
            devRetrofit.baseUrl().toString()
        )
        assertEquals(
            "https://staging-api.example.com/",
            stagingRetrofit.baseUrl().toString()
        )
        assertEquals(
            "https://api.example.com/",
            prodRetrofit.baseUrl().toString()
        )
        
        // All base URLs should be different
        assertNotEquals(devRetrofit.baseUrl(), stagingRetrofit.baseUrl())
        assertNotEquals(stagingRetrofit.baseUrl(), prodRetrofit.baseUrl())
        assertNotEquals(devRetrofit.baseUrl(), prodRetrofit.baseUrl())
    }
    
    /**
     * Test dynamic base URL switching with interceptor.
     */
    @Test
    fun `interceptor can switch base URL dynamically`() = runBlocking {
        // Arrange: Mock responses from different servers
        devServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"environment":"dev","version":"1.0"}""")
        )
        stagingServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"environment":"staging","version":"1.1"}""")
        )
        
        // Create custom environments with mock server URLs
        val devUrl = devServer.url("/").toString()
        val stagingUrl = stagingServer.url("/").toString()
        
        // Create Retrofit with dynamic base URL
        val devEnv = BaseUrlSwitch.Environment.DEVELOPMENT
        val (retrofit, interceptor) = BaseUrlSwitch.createWithDynamicBaseUrl(devEnv)
        
        // Override with dev server URL
        interceptor.setBaseUrl(devUrl.toHttpUrl())
        
        val service = retrofit.create(BaseUrlSwitch.ApiService::class.java)
        
        // Act: Make request to dev
        val devResponse = service.getInfo()
        
        // Switch to staging
        interceptor.setBaseUrl(stagingUrl.toHttpUrl())
        
        // Act: Make request to staging
        val stagingResponse = service.getInfo()
        
        // Assert: Responses should come from different servers
        assertEquals("dev", devResponse.environment)
        assertEquals("staging", stagingResponse.environment)
        
        // Verify correct servers were hit
        assertEquals(1, devServer.requestCount)
        assertEquals(1, stagingServer.requestCount)
    }
    
    /**
     * Test BaseUrlInterceptor getBaseUrl method.
     */
    @Test
    fun `interceptor tracks current base URL`() {
        // Arrange
        val initialUrl = "https://initial.example.com/".toHttpUrl()
        val interceptor = BaseUrlSwitch.BaseUrlInterceptor(initialUrl)
        
        // Assert: Initial URL should be set
        assertEquals(initialUrl, interceptor.getBaseUrl())
        
        // Act: Change base URL
        val newUrl = "https://new.example.com/".toHttpUrl()
        interceptor.setBaseUrl(newUrl)
        
        // Assert: URL should be updated
        assertEquals(newUrl, interceptor.getBaseUrl())
    }
    
    /**
     * Test EnvironmentManager switches environments correctly.
     */
    @Test
    fun `EnvironmentManager switches environments`() {
        // Arrange: Create manager starting with dev
        val manager = BaseUrlSwitch.EnvironmentManager(BaseUrlSwitch.Environment.DEVELOPMENT)
        
        // Assert: Initial environment should be dev
        assertEquals(BaseUrlSwitch.Environment.DEVELOPMENT, manager.getCurrentEnvironment())
        
        // Act: Switch to staging
        manager.switchEnvironment(BaseUrlSwitch.Environment.STAGING)
        
        // Assert: Environment should be updated
        assertEquals(BaseUrlSwitch.Environment.STAGING, manager.getCurrentEnvironment())
        
        // Act: Switch to production
        manager.switchEnvironment(BaseUrlSwitch.Environment.PRODUCTION)
        
        // Assert: Environment should be updated again
        assertEquals(BaseUrlSwitch.Environment.PRODUCTION, manager.getCurrentEnvironment())
    }
    
    /**
     * Test EnvironmentManager creates services with correct base URL.
     */
    @Test
    fun `EnvironmentManager creates services for current environment`() {
        // Arrange: Create manager with different environments
        val devManager = BaseUrlSwitch.EnvironmentManager(BaseUrlSwitch.Environment.DEVELOPMENT)
        val prodManager = BaseUrlSwitch.EnvironmentManager(BaseUrlSwitch.Environment.PRODUCTION)
        
        // Act: Get Retrofit instances
        val devRetrofit = devManager.getCurrentRetrofit()
        val prodRetrofit = prodManager.getCurrentRetrofit()
        
        // Assert: Each should have correct base URL
        assertEquals(
            BaseUrlSwitch.Environment.DEVELOPMENT.baseUrl,
            devRetrofit.baseUrl().toString()
        )
        assertEquals(
            BaseUrlSwitch.Environment.PRODUCTION.baseUrl,
            prodRetrofit.baseUrl().toString()
        )
    }
    
    /**
     * Test EnvironmentManager caches Retrofit instances.
     */
    @Test
    fun `EnvironmentManager caches Retrofit instances`() {
        // Arrange
        val manager = BaseUrlSwitch.EnvironmentManager(BaseUrlSwitch.Environment.DEVELOPMENT)
        
        // Act: Get Retrofit instance twice
        val retrofit1 = manager.getCurrentRetrofit()
        val retrofit2 = manager.getCurrentRetrofit()
        
        // Assert: Should return same instance
        assertSame("Should cache and reuse Retrofit instance", retrofit1, retrofit2)
    }
    
    /**
     * Test EnvironmentManager creates new instance after environment switch.
     */
    @Test
    fun `EnvironmentManager uses different instances after switch`() {
        // Arrange
        val manager = BaseUrlSwitch.EnvironmentManager(BaseUrlSwitch.Environment.DEVELOPMENT)
        
        // Act: Get dev instance
        val devRetrofit = manager.getCurrentRetrofit()
        
        // Switch to staging
        manager.switchEnvironment(BaseUrlSwitch.Environment.STAGING)
        val stagingRetrofit = manager.getCurrentRetrofit()
        
        // Assert: Should be different instances with different base URLs
        assertNotSame("Should use different Retrofit instances", devRetrofit, stagingRetrofit)
        assertNotEquals(
            "Should have different base URLs",
            devRetrofit.baseUrl(),
            stagingRetrofit.baseUrl()
        )
    }
    
    /**
     * Test EnvironmentManager createService convenience method.
     */
    @Test
    fun `EnvironmentManager createService works correctly`() {
        // Arrange
        val manager = BaseUrlSwitch.EnvironmentManager(BaseUrlSwitch.Environment.DEVELOPMENT)
        
        // Act: Create service using convenience method
        val service = manager.createService<BaseUrlSwitch.ApiService>()
        
        // Assert: Service should be created
        assertNotNull("Service should be created", service)
    }
    
    /**
     * Test that requests use the correct URL after environment switch.
     */
    @Test
    fun `environment switch affects subsequent requests`() = runBlocking {
        // Arrange: Mock responses
        devServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"environment":"dev","version":"1.0"}""")
        )
        prodServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"environment":"prod","version":"2.0"}""")
        )
        
        val devUrl = devServer.url("/").toString()
        val prodUrl = prodServer.url("/").toString()
        
        // Create interceptor
        val (retrofit, interceptor) = BaseUrlSwitch.createWithDynamicBaseUrl(
            BaseUrlSwitch.Environment.DEVELOPMENT
        )
        interceptor.setBaseUrl(devUrl.toHttpUrl())
        
        val service = retrofit.create(BaseUrlSwitch.ApiService::class.java)
        
        // Act: Request to dev
        service.getInfo()
        
        // Switch to prod
        interceptor.setBaseUrl(prodUrl.toHttpUrl())
        
        // Act: Request to prod
        service.getInfo()
        
        // Assert: Both servers should have been hit once
        assertEquals("Dev server should receive one request", 1, devServer.requestCount)
        assertEquals("Prod server should receive one request", 1, prodServer.requestCount)
    }
    
    /**
     * Test Environment enum values.
     */
    @Test
    fun `Environment enum has correct values`() {
        // Assert: All environments should have correct base URLs
        assertEquals(
            "https://dev-api.example.com/",
            BaseUrlSwitch.Environment.DEVELOPMENT.baseUrl
        )
        assertEquals(
            "https://staging-api.example.com/",
            BaseUrlSwitch.Environment.STAGING.baseUrl
        )
        assertEquals(
            "https://api.example.com/",
            BaseUrlSwitch.Environment.PRODUCTION.baseUrl
        )
    }
}

package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response as OkHttpResponse
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.atomic.AtomicInteger

/**
 * InterceptorOrderSampleTest: Demonstrates interceptor execution order.
 * 
 * Learning nodes:
 * - L2-1: Application interceptor behavior
 * - L2-2: Network interceptor behavior
 * 
 * This test demonstrates:
 * 1. Application interceptors run before network interceptors
 * 2. Interceptors execute in the order they are added
 * 3. Each interceptor in the chain is invoked once per request
 * 4. Application interceptors see the original request
 * 5. Network interceptors see the actual network request
 * 
 * Source reading notes:
 * - Application interceptors: Added with addInterceptor()
 * - Network interceptors: Added with addNetworkInterceptor()
 * - Order: Application -> OkHttp internal -> Network -> Server
 * - Application interceptors can short-circuit (return cached response)
 * - Network interceptors see redirects and retries
 */
class InterceptorOrderSampleTest {
    
    /**
     * Simple data model for testing.
     */
    data class TestData(
        val id: Int,
        val value: String
    )
    
    /**
     * Service interface for interceptor testing.
     */
    interface InterceptorService {
        @GET("data")
        suspend fun getData(): TestData
    }
    
    /**
     * Counting interceptor that tracks invocation order.
     */
    class CountingInterceptor(
        private val name: String,
        private val orderList: MutableList<String>,
        private val counter: AtomicInteger
    ) : Interceptor {
        var invocationCount = 0
            private set
        
        override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
            invocationCount++
            val order = counter.incrementAndGet()
            orderList.add("$name-$order")
            return chain.proceed(chain.request())
        }
    }
    
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
     * Test that application interceptors execute before network interceptors.
     */
    @Test
    fun `application interceptors execute before network interceptors`() = runBlocking {
        // Arrange: Create tracking structures
        val orderList = mutableListOf<String>()
        val counter = AtomicInteger(0)
        
        val appInterceptor = CountingInterceptor("APP", orderList, counter)
        val networkInterceptor = CountingInterceptor("NET", orderList, counter)
        
        // Create OkHttpClient with both interceptor types
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(appInterceptor)
            .addNetworkInterceptor(networkInterceptor)
            .build()
        
        // Create Retrofit
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        val service = retrofit.create(InterceptorService::class.java)
        
        // Mock response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"test"}""")
        )
        
        // Act
        service.getData()
        
        // Assert: Application interceptor should execute first
        assertEquals(2, orderList.size)
        assertTrue("Application interceptor should execute first", orderList[0].startsWith("APP"))
        assertTrue("Network interceptor should execute second", orderList[1].startsWith("NET"))
        
        assertEquals(1, appInterceptor.invocationCount)
        assertEquals(1, networkInterceptor.invocationCount)
    }
    
    /**
     * Test that multiple application interceptors execute in order.
     */
    @Test
    fun `multiple application interceptors execute in added order`() = runBlocking {
        // Arrange
        val orderList = mutableListOf<String>()
        val counter = AtomicInteger(0)
        
        val interceptor1 = CountingInterceptor("APP1", orderList, counter)
        val interceptor2 = CountingInterceptor("APP2", orderList, counter)
        val interceptor3 = CountingInterceptor("APP3", orderList, counter)
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor1)
            .addInterceptor(interceptor2)
            .addInterceptor(interceptor3)
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        val service = retrofit.create(InterceptorService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"test"}""")
        )
        
        // Act
        service.getData()
        
        // Assert: Order should match addition order
        assertEquals(3, orderList.size)
        assertTrue("First should be APP1", orderList[0].startsWith("APP1"))
        assertTrue("Second should be APP2", orderList[1].startsWith("APP2"))
        assertTrue("Third should be APP3", orderList[2].startsWith("APP3"))
    }
    
    /**
     * Test that interceptors are invoked once per request.
     */
    @Test
    fun `interceptors are invoked once per request`() = runBlocking {
        // Arrange
        val orderList = mutableListOf<String>()
        val counter = AtomicInteger(0)
        
        val interceptor = CountingInterceptor("APP", orderList, counter)
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        val service = retrofit.create(InterceptorService::class.java)
        
        // Make 3 requests
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"id":$it,"value":"test$it"}""")
            )
        }
        
        // Act
        service.getData()
        service.getData()
        service.getData()
        
        // Assert: Should be invoked 3 times (once per request)
        assertEquals(3, interceptor.invocationCount)
        assertEquals(3, orderList.size)
    }
    
    /**
     * Test complete interceptor chain order.
     */
    @Test
    fun `complete interceptor chain executes in correct order`() = runBlocking {
        // Arrange: Create full chain
        val orderList = mutableListOf<String>()
        val counter = AtomicInteger(0)
        
        val app1 = CountingInterceptor("APP1", orderList, counter)
        val app2 = CountingInterceptor("APP2", orderList, counter)
        val net1 = CountingInterceptor("NET1", orderList, counter)
        val net2 = CountingInterceptor("NET2", orderList, counter)
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(app1)
            .addInterceptor(app2)
            .addNetworkInterceptor(net1)
            .addNetworkInterceptor(net2)
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        val service = retrofit.create(InterceptorService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"test"}""")
        )
        
        // Act
        service.getData()
        
        // Assert: Verify complete order
        assertEquals(4, orderList.size)
        assertTrue("First: APP1", orderList[0].startsWith("APP1"))
        assertTrue("Second: APP2", orderList[1].startsWith("APP2"))
        assertTrue("Third: NET1", orderList[2].startsWith("NET1"))
        assertTrue("Fourth: NET2", orderList[3].startsWith("NET2"))
        
        // Verify each was called exactly once
        assertEquals(1, app1.invocationCount)
        assertEquals(1, app2.invocationCount)
        assertEquals(1, net1.invocationCount)
        assertEquals(1, net2.invocationCount)
    }
    
    /**
     * Test that logging interceptors can track request/response flow.
     */
    @Test
    fun `logging interceptor tracks request and response`() = runBlocking {
        // Arrange: Create logging interceptor
        val logs = mutableListOf<String>()
        
        class LoggingInterceptor : Interceptor {
            override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
                val request = chain.request()
                logs.add("REQUEST: ${request.method} ${request.url.encodedPath}")
                
                val response = chain.proceed(request)
                
                logs.add("RESPONSE: ${response.code}")
                return response
            }
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(LoggingInterceptor())
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        val service = retrofit.create(InterceptorService::class.java)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":1,"value":"test"}""")
        )
        
        // Act
        service.getData()
        
        // Assert: Should have logged request and response
        assertEquals(2, logs.size)
        assertTrue("Should log request", logs[0].contains("REQUEST: GET /data"))
        assertTrue("Should log response", logs[1].contains("RESPONSE: 200"))
    }
}

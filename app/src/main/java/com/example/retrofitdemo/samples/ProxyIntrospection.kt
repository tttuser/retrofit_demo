package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.lang.reflect.Proxy

/**
 * ProxyIntrospection: Demonstrates Retrofit's dynamic proxy mechanism.
 * 
 * Learning nodes:
 * - L3-1: Understanding Retrofit's proxy-based service implementation
 * - L3-2: Method introspection and invocation handler behavior
 * 
 * This sample demonstrates:
 * 1. Retrofit uses Java's Proxy API to create service implementations
 * 2. Service interfaces are backed by InvocationHandler
 * 3. Method signatures determine HTTP request characteristics
 * 4. Proxy instances can be introspected at runtime
 * 
 * Key insights:
 * - Service objects are not real classes but dynamic proxies
 * - Each method call is intercepted and converted to HTTP request
 * - Proxy.isProxyClass() can detect Retrofit services
 * - InvocationHandler converts method calls to OkHttp Call objects
 * 
 * Source reading notes:
 * - See Retrofit.create() which uses Proxy.newProxyInstance()
 * - ServiceMethod.parseAnnotations() interprets method annotations
 * - Platform determines default call adapter and converter
 */
object ProxyIntrospection {
    
    /**
     * Simple data class for API response.
     */
    data class Item(val id: Int, val name: String)
    
    /**
     * Test service interface for proxy inspection.
     */
    interface ProxyTestService {
        @GET("items/{id}")
        suspend fun getItem(@Path("id") id: Int): Item
        
        @GET("items")
        suspend fun listItems(): List<Item>
    }
    
    /**
     * Creates a Retrofit instance for proxy testing.
     * 
     * @param baseUrl Base URL for the API
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
     * Checks if a service object is a Java dynamic proxy.
     * 
     * Retrofit services are always proxies, not real class instances.
     * 
     * @param service The service instance to check
     * @return True if the service is a proxy instance
     */
    fun isProxy(service: Any): Boolean {
        return Proxy.isProxyClass(service.javaClass)
    }
    
    /**
     * Gets the interfaces implemented by a proxy service.
     * 
     * For Retrofit services, this returns the service interface.
     * 
     * @param service The service instance
     * @return Array of interfaces implemented by the proxy
     */
    fun getProxyInterfaces(service: Any): Array<Class<*>> {
        return service.javaClass.interfaces
    }
    
    /**
     * Extracts proxy information from a Retrofit service.
     * 
     * This demonstrates introspection capabilities:
     * - Verify it's a proxy
     * - Get implemented interfaces
     * - List methods on the interface
     * 
     * @param service The service instance to introspect
     * @return Map containing proxy information
     */
    fun introspectService(service: ProxyTestService): Map<String, Any> {
        val serviceClass = service.javaClass
        val isProxy = Proxy.isProxyClass(serviceClass)
        val interfaces = serviceClass.interfaces
        val methods = if (interfaces.isNotEmpty()) {
            interfaces[0].declaredMethods.map { it.name }
        } else {
            emptyList()
        }
        
        return mapOf(
            "isProxy" to isProxy,
            "className" to serviceClass.name,
            "interfaces" to interfaces.map { it.name },
            "methods" to methods
        )
    }
    
    /**
     * Demonstrates that multiple service instances share the same proxy class.
     * 
     * Retrofit caches and reuses proxy classes for the same interface.
     * 
     * @param retrofit The Retrofit instance
     * @return True if both service instances have the same class
     */
    fun sameProxyClass(retrofit: Retrofit): Boolean {
        val service1 = retrofit.create(ProxyTestService::class.java)
        val service2 = retrofit.create(ProxyTestService::class.java)
        return service1.javaClass === service2.javaClass
    }
}

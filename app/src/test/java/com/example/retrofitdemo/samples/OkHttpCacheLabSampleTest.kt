package com.example.retrofitdemo.samples

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpCacheLabSampleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun secondCall_isServedFromCache_whenResponseIsCacheable() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            // A cacheable response.
            // Note: We avoid ETag/Last-Modified complexity; max-age is enough for deterministic tests.
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Cache-Control", "public, max-age=60")
                    .setBody("""{"value":"v1"}""")
            )

            val cacheDir = tmp.newFolder("http-cache")
            val api = OkHttpCacheLabSample.createApi(
                baseUrl = server.url("/").toString(),
                cacheDir = cacheDir
            )

            val first = api.payload()
            assertEquals("v1", first.value)

            // No more enqueued responses. If client tries the network again, requestCount will increase
            // and (depending on timing) it may fail. But with a fresh cacheable response, it should
            // serve from cache without hitting server.
            val second = api.payload()
            assertEquals("v1", second.value)

            // Exactly one request should have been made to the server.
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun forceCache_canReturnCachedResponse_evenIfServerIsDown() = runBlocking {
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString()

        val cacheDir = tmp.newFolder("http-cache-2")

        // Build a client with a cache and an interceptor that forces cache on every request.
        val cache = Cache(cacheDir, 2L * 1024L * 1024L)

        val okHttp = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(OkHttpCacheLabSample.forceCacheInterceptor())
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val api = retrofit.create(OkHttpCacheLabSample.Api::class.java)

        try {
            // First, make the cache warm with a normal cacheable response.
            // Because we force cache, the *first* request must still reach network (no cache yet),
            // so we enqueue it.
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Cache-Control", "public, max-age=60")
                    .setBody("""{"value":"cached"}""")
            )

            val first = api.payload()
            assertEquals("cached", first.value)
            assertEquals(1, server.requestCount)

            // Now shut down the server to guarantee there is no network.
            server.shutdown()

            // Second call should be satisfied from cache due to only-if-cached.
            val second = api.payload()
            assertEquals("cached", second.value)
        } finally {
            // server already shutdown above in the happy path; ignore if double shutdown throws.
            runCatching { server.shutdown() }
        }
    }

    @Test
    fun forceCache_withoutCachedEntry_shouldFailWith504() {
        runBlocking {
            val server = MockWebServer()
            server.start()
            val baseUrl = server.url("/").toString()

            val cacheDir = tmp.newFolder("http-cache-3")
            val cache = Cache(cacheDir, 2L * 1024L * 1024L)

            val okHttp = OkHttpClient.Builder()
                .cache(cache)
                .addInterceptor(OkHttpCacheLabSample.forceCacheInterceptor())
                .build()

            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            val api = retrofit.create(OkHttpCacheLabSample.Api::class.java)

            try {
                // No enqueue, and we force cache. With no cached entry, OkHttp returns a synthetic 504.
                runCatching { api.payload() }.onSuccess {
                    throw AssertionError("Expected failure when forcing cache with empty cache")
                }.onFailure { t ->
                    // For suspend functions, Retrofit will throw HttpException for non-2xx in many setups.
                    // But depending on call adapter/Retrofit version, it might throw a different exception.
                    // We assert the message contains 504 or 'Unsatisfiable Request'.
                    val msg = t.message.orEmpty()
                    assertTrue(
                        "Expected 504/Unsatisfiable Request but was: ${t::class.java.name}: $msg",
                        msg.contains("504") || msg.contains("Unsatisfiable", ignoreCase = true)
                    )
                }
            } finally {
                server.shutdown()
            }
        }
    }
}
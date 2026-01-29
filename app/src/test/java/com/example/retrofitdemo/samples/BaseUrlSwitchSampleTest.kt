package com.example.retrofitdemo.samples

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlSwitchSampleTest {

    @Test
    fun rebuildRetrofit_switchesBaseUrlByCreatingNewInstance() = runBlocking {
        val serverA = MockWebServer()
        val serverB = MockWebServer()
        serverA.start()
        serverB.start()

        try {
            serverA.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"ok":true}""")
            )
            serverB.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"ok":true}""")
            )

            val apiA = BaseUrlSwitchSample.createApiByRebuilding(
                baseUrl = serverA.url("/").toString()
            )
            val apiB = BaseUrlSwitchSample.createApiByRebuilding(
                baseUrl = serverB.url("/").toString()
            )

            val a = apiA.ping()
            val b = apiB.ping()
            assertTrue(a.ok)
            assertTrue(b.ok)

            assertEquals(1, serverA.requestCount)
            assertEquals(1, serverB.requestCount)

            val reqA = serverA.takeRequest()
            val reqB = serverB.takeRequest()
            assertEquals("/ping", reqA.path)
            assertEquals("/ping", reqB.path)
        } finally {
            serverA.shutdown()
            serverB.shutdown()
        }
    }

    @Test
    fun hostRewriteInterceptor_routesRequestsToTargetHost() = runBlocking {
        val fixed = MockWebServer()
        val target = MockWebServer()
        fixed.start()
        target.start()

        try {
            // We expect *no* calls to fixed once rewrite is active.
            target.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"ok":true}""")
            )

            val api = BaseUrlSwitchSample.createApiByHostRewrite(
                fixedBaseUrl = fixed.url("/").toString(),
                targetBaseUrl = target.url("/").toString()
            )

            val res = api.ping()
            assertTrue(res.ok)

            assertEquals(
                "Request should be routed to target server",
                1,
                target.requestCount
            )
            assertEquals(
                "Fixed server should not receive the request after rewrite",
                0,
                fixed.requestCount
            )

            val recorded = target.takeRequest()
            assertEquals("/ping", recorded.path)
        } finally {
            fixed.shutdown()
            target.shutdown()
        }
    }

    @Test
    fun retrofitBaseUrl_requiresTrailingSlash() {
        val ex = runCatching {
            // Missing trailing slash: ".../api" (Retrofit requires ".../api/")
            BaseUrlSwitchSample.createApiExpectingTrailingSlashRule("https://example.com/api")
        }.exceptionOrNull()

        assertTrue("Expected IllegalArgumentException, got: ${ex?.javaClass}", ex is IllegalArgumentException)
        val msg = ex?.message.orEmpty()
        // Keep message assertion flexible across Retrofit versions.
        assertTrue(
            "Expected message to mention trailing slash or end in /, but was: $msg",
            msg.contains("end in /", ignoreCase = true) ||
                msg.contains("trailing", ignoreCase = true) ||
                msg.contains("/", ignoreCase = true)
        )
    }
}
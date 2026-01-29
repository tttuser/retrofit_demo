package com.example.retrofit

import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.div
import kotlin.system.measureNanoTime

/**
 * Instrumentation test to verify Retrofit method parsing happens once per Method
 * and subsequent calls are faster due to Retrofit's internal ServiceMethod cache.
 *
 * Important: This test DOES NOT execute/enqueue calls, so no network request is sent.
 *
 * How to use with Android Studio CPU Profiler:
 * 1) Run this test from Android Studio (androidTest).
 * 2) Attach CPU Profiler to the test process and record while the test runs.
 * 3) In Call Chart/Flame Chart, search for:
 *    - retrofit2.ServiceMethod
 *    - parseAnnotations
 *    - retrofit2.RequestFactory
 */
class RetrofitMethodCacheInstrumentationTest {

    interface Api {
        @GET("users/{id}")
        fun user(@Path("id") id: String): retrofit2.Call<ResponseBody>
    }

    val api by lazy {
        println("api init: " + Thread.currentThread().toString())
        init()
    }

    private fun init(): Api {
        val okHttp: Call.Factory = OkHttpClient.Builder()
            // No interceptor needed: we never execute/enqueue, so no I/O happens.
            .build()

        val retrofit = Retrofit.Builder()
            // Base URL must be valid, but it will not be contacted since we won't execute the Call.
            .baseUrl("https://example.invalid/")
            .callFactory(okHttp)
            .build()

       return retrofit.create(Api::class.java)
    }

    fun cold() {
        // ---- Cold call: triggers annotation parsing + cache put (first time only) ----
        val coldNs = measureNanoTime {
            val call = api.user("1")   // parsing + build request + create Call wrapper
            call.request()             // still no network; just ensures Request path is exercised
        }
        println("  cold (first call)   = ${coldNs / 1_000_000.0} ms")
    }

    fun warm() {
        // ---- Warm loop: should hit cache, avoiding parseAnnotations on each iteration ----
        val iterations = 200
        val warmTotalNs = measureNanoTime {
            repeat(iterations) {
                val call = api.user("1") // should be cache hit after first call
                call.request()
            }
        }
        val warmAvgNs = warmTotalNs / iterations.toDouble()

        // Print results to Instrumentation output (visible in Run window / Logcat).
        // (No assertions on timing because devices/CI vary; use profiler + logs.)
        println("Retrofit method parsing cache test:")
        println("  warm total          = ${warmTotalNs / 1_000_000.0} ms for $iterations calls")
        println("  warm average         = ${warmAvgNs} ns/call")
    }

}
package com.example.retrofitdemo.samples

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * L1/L2 - BaseUrlSwitch
 *
 * Demonstrates two patterns:
 * 1) Rebuild Retrofit with a different baseUrl (simple, safest).
 * 2) Keep Retrofit fixed and rewrite the request URL (host/scheme/port) via OkHttp interceptor.
 *
 * Notes:
 * - Retrofit requires baseUrl to end with '/'.
 *   e.g. "https://example.com/api/" is OK, but "https://example.com/api" will throw.
 * - When rewriting URLs, you typically must preserve the encoded path from the original request.
 */
object BaseUrlSwitchSample {

    data class Ping(val ok: Boolean)

    interface Api {
        @GET("/ping")
        suspend fun ping(): Ping

        @GET("/echo/{value}")
        suspend fun echo(@Path("value") value: String): Ping
    }

    fun createApiByRebuilding(
        baseUrl: String,
        okHttpClient: OkHttpClient = OkHttpClient()
    ): Api {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl) // must end with /
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(Api::class.java)
    }

    /**
     * Creates an Api that always *builds* requests against [fixedBaseUrl], but will rewrite the
     * outgoing request to target [targetBaseUrl] (scheme/host/port).
     *
     * This is useful when:
     * - You want to keep a single Retrofit instance (e.g., for DI graph)
     * - You want to route traffic to different hosts at runtime (staging/prod, region-based, etc.)
     */
    fun createApiByHostRewrite(
        fixedBaseUrl: String,
        targetBaseUrl: String
    ): Api {
        val fixedHttpUrl = fixedBaseUrl.toHttpUrl()
        val targetHttpUrl = targetBaseUrl.toHttpUrl()

        val rewriteInterceptor = HostRewriteInterceptor(
            fromHost = fixedHttpUrl.host,
            toBaseUrl = targetHttpUrl
        )

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(rewriteInterceptor)
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(fixedBaseUrl) // must end with /
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(Api::class.java)
    }

    /**
     * Rewrites scheme/host/port while preserving the full encoded path + query from the original URL.
     */
    class HostRewriteInterceptor(
        private val fromHost: String,
        private val toBaseUrl: HttpUrl
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original: Request = chain.request()
            val originalUrl = original.url

            val newUrl = if (originalUrl.host == fromHost) {
                originalUrl.newBuilder()
                    .scheme(toBaseUrl.scheme)
                    .host(toBaseUrl.host)
                    .port(toBaseUrl.port)
                    .build()
            } else {
                originalUrl
            }

            val newRequest = if (newUrl != originalUrl) {
                original.newBuilder().url(newUrl).build()
            } else {
                original
            }

            return chain.proceed(newRequest)
        }
    }

    /**
     * Helper purely to demonstrate Retrofit's baseUrl trailing-slash rule.
     * Calling this with a baseUrl missing the trailing slash will throw IllegalArgumentException.
     */
    fun createApiExpectingTrailingSlashRule(baseUrl: String): Api {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(baseUrl) // throws if missing trailing '/'
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Api::class.java)
    }
}
package com.example.retrofitdemo.samples

import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import java.io.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * L1/L2 - OkHttpCacheLab
 *
 * Demonstrates:
 * - How to configure OkHttp's on-disk HTTP cache
 * - How request Cache-Control affects cache usage
 * - How to switch between "online" and "forced cache" behavior
 *
 * Source reading notes:
 * - OkHttp Cache honors HTTP caching headers (Cache-Control / Expires / ETag / Last-Modified).
 * - `CacheControl.FORCE_CACHE` forces OkHttp to return cached response (or 504 Unsatisfiable Request).
 * - For deterministic unit tests, prefer responses with explicit `Cache-Control: max-age=...`.
 */
object OkHttpCacheLabSample {

    data class Payload(val value: String)

    interface Api {
        @GET("/payload")
        @Headers("Accept: application/json")
        suspend fun payload(
            @Header("Cache-Control") cacheControl: String? = null
        ): Payload
    }

    fun createApi(
        baseUrl: String,
        cacheDir: File,
        cacheSizeBytes: Long = 2L * 1024L * 1024L,
        extraNetworkInterceptor: Interceptor? = null,
    ): Api {
        val cache = Cache(cacheDir, cacheSizeBytes)

        val okHttp = OkHttpClient.Builder()
            .cache(cache)
            .apply {
                if (extraNetworkInterceptor != null) {
                    // networkInterceptors see network responses (and cache writes)
                    addNetworkInterceptor(extraNetworkInterceptor)
                }
            }
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl) // must end with /
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(Api::class.java)
    }

    /**
     * Returns an interceptor that forces cache usage for all requests.
     *
     * Equivalent idea:
     * ```
     * request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
     * ```
     * but expressed via header for easy testing.
     */
    fun forceCacheInterceptor(): Interceptor = Interceptor { chain ->
        val original: Request = chain.request()
        val forced: Request = original.newBuilder()
            .header("Cache-Control", "only-if-cached,max-stale=2147483647")
            .build()
        chain.proceed(forced)
    }

    /**
     * Returns an interceptor that disables cache for all requests (always go network).
     */
    fun noCacheInterceptor(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val noCache = original.newBuilder()
            .header("Cache-Control", "no-cache")
            .build()
        chain.proceed(noCache)
    }
}
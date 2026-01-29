package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.lang.reflect.Type
import java.util.Base64

/**
 * DecryptThenParse: Demonstrates custom converter for decryption before JSON parsing.
 * 
 * Learning nodes:
 * - L2-5: Custom converters
 * - L4-3: Response transformation pipelines
 * 
 * This sample demonstrates:
 * 1. Creating a custom Retrofit Converter.Factory
 * 2. Decrypting response body before JSON parsing
 * 3. Chaining converters (decrypt → parse)
 * 4. Handling encrypted API responses
 * 
 * Source reading notes:
 * - Converter.Factory creates converters for request/response bodies
 * - Multiple converters can be chained (executed in order)
 * - DecryptingConverterFactory decrypts, then delegates to MoshiConverterFactory
 * - Useful for APIs that encrypt sensitive data in transit
 * - This example uses simple Base64 encoding (not real encryption!)
 */
object DecryptThenParse {
    
    /**
     * Simple data model for testing.
     */
    data class SecureData(
        val id: Int,
        val sensitiveInfo: String,
        val timestamp: Long
    )
    
    /**
     * Service interface expecting encrypted responses.
     */
    interface SecureService {
        /**
         * Gets encrypted data that will be decrypted and parsed automatically.
         */
        @GET("secure/data")
        suspend fun getSecureData(): SecureData
        
        /**
         * Gets a list of encrypted items.
         */
        @GET("secure/list")
        suspend fun getSecureList(): List<SecureData>
    }
    
    /**
     * Simple "decryptor" that decodes Base64.
     * In production, this would use actual encryption (AES, RSA, etc.).
     */
    interface Decryptor {
        fun decrypt(encrypted: String): String
    }
    
    /**
     * Base64 decryptor for demonstration.
     * Real implementations would use proper encryption algorithms.
     */
    class Base64Decryptor : Decryptor {
        override fun decrypt(encrypted: String): String {
            return try {
                val decoded = Base64.getDecoder().decode(encrypted.trim())
                String(decoded, Charsets.UTF_8)
            } catch (e: Exception) {
                // If decryption fails, return original (might not be encrypted)
                encrypted
            }
        }
    }
    
    /**
     * Custom Converter.Factory that decrypts response before parsing.
     * 
     * This factory:
     * 1. Intercepts response body conversion
     * 2. Decrypts the response using provided Decryptor
     * 3. Delegates to next converter (Moshi) for JSON parsing
     */
    class DecryptingConverterFactory(
        private val decryptor: Decryptor,
        private val delegate: Converter.Factory
    ) : Converter.Factory() {
        
        override fun responseBodyConverter(
            type: Type,
            annotations: Array<out Annotation>,
            retrofit: Retrofit
        ): Converter<ResponseBody, *>? {
            // Get the delegate converter (e.g., MoshiConverterFactory)
            val delegateConverter = delegate.responseBodyConverter(type, annotations, retrofit)
                ?: return null
            
            // Return our decrypting converter that wraps the delegate
            return DecryptingConverter(decryptor, delegateConverter)
        }
        
        /**
         * Converter that decrypts response body before delegating to JSON parser.
         */
        private class DecryptingConverter<T>(
            private val decryptor: Decryptor,
            private val delegate: Converter<ResponseBody, T>
        ) : Converter<ResponseBody, T> {
            
            override fun convert(value: ResponseBody): T {
                // Read the encrypted response
                val encrypted = value.string()
                
                // Decrypt it
                val decrypted = decryptor.decrypt(encrypted)
                
                // Create a new ResponseBody with decrypted content
                val contentType = "application/json".toMediaType()
                val decryptedBody = decrypted.toResponseBody(contentType)
                
                // Delegate to JSON parser
                return delegate.convert(decryptedBody)!!
            }
        }
    }
    
    /**
     * Creates a Retrofit instance with DecryptingConverterFactory.
     * 
     * @param baseUrl The base URL for the API
     * @param decryptor The decryptor to use
     * @return Configured Retrofit instance
     */
    fun createRetrofit(baseUrl: String, decryptor: Decryptor = Base64Decryptor()): Retrofit {
        val okHttpClient = OkHttpClient.Builder().build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        
        val moshiFactory = MoshiConverterFactory.create(moshi)
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            // Add decrypting converter first, it delegates to Moshi
            .addConverterFactory(DecryptingConverterFactory(decryptor, moshiFactory))
            .build()
    }
    
    /**
     * Creates the SecureService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return SecureService implementation
     */
    fun createService(retrofit: Retrofit): SecureService {
        return retrofit.create(SecureService::class.java)
    }
    
    /**
     * Helper to encrypt data for testing.
     * Encrypts a JSON string using Base64.
     */
    fun encryptForTesting(json: String): String {
        return Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * Use cases for encrypted API responses.
     */
    enum class UseCase(val description: String) {
        SENSITIVE_DATA("Protect sensitive user data (PII, financial info)"),
        COMPLIANCE("Meet regulatory requirements (HIPAA, GDPR)"),
        API_SECURITY("Prevent data interception by proxies or MITM"),
        CUSTOM_PROTOCOL("Implement proprietary data format"),
        OBFUSCATION("Make reverse engineering more difficult")
    }
    
    /**
     * Gets all use cases for encrypted responses.
     */
    fun getUseCases(): List<UseCase> {
        return UseCase.values().toList()
    }
}

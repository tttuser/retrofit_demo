package com.example.retrofitdemo.samples

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

/**
 * FormAndMultipartSample: Demonstrates form-encoded and multipart requests.
 * 
 * Learning node:
 * - L1-2: Form-encoded and multipart request handling
 * 
 * This sample demonstrates:
 * 1. Form-encoded POST requests (@FormUrlEncoded, @Field)
 * 2. Multipart POST requests (@Multipart, @Part)
 * 3. Different content types for different request types
 * 4. File upload simulation with multipart
 * 
 * Source reading notes:
 * - @FormUrlEncoded: Content-Type is application/x-www-form-urlencoded
 * - @Field: Parameters are encoded as key=value pairs
 * - @Multipart: Content-Type is multipart/form-data with boundary
 * - @Part: Each part can have different content type
 * - Multipart is used for file uploads and binary data
 */
object FormAndMultipartSample {
    
    /**
     * Response for login endpoint.
     */
    data class LoginResult(
        val success: Boolean,
        val token: String?,
        val userId: Long?
    )
    
    /**
     * Response for file upload.
     */
    data class UploadResult(
        val success: Boolean,
        val fileId: String,
        val filename: String,
        val size: Long
    )
    
    /**
     * Service interface demonstrating form and multipart requests.
     */
    interface FormService {
        /**
         * Form-encoded login request.
         * POST /login with application/x-www-form-urlencoded body
         * 
         * @param username Username field
         * @param password Password field
         * @return LoginResult with token
         */
        @FormUrlEncoded
        @POST("login")
        suspend fun login(
            @Field("username") username: String,
            @Field("password") password: String
        ): LoginResult
        
        /**
         * Form-encoded request with multiple fields.
         * POST /register with application/x-www-form-urlencoded body
         * 
         * @param email User email
         * @param username Username
         * @param age User age
         * @param agreedToTerms Whether user agreed to terms
         * @return LoginResult with registration result
         */
        @FormUrlEncoded
        @POST("register")
        suspend fun register(
            @Field("email") email: String,
            @Field("username") username: String,
            @Field("age") age: Int,
            @Field("agreed_to_terms") agreedToTerms: Boolean
        ): LoginResult
        
        /**
         * Multipart file upload request.
         * POST /upload with multipart/form-data body
         * 
         * @param file The file part
         * @param description Description field
         * @return UploadResult with file details
         */
        @Multipart
        @POST("upload")
        suspend fun uploadFile(
            @Part file: MultipartBody.Part,
            @Part("description") description: String
        ): UploadResult
    }
    
    /**
     * Creates a Retrofit instance for FormService.
     * 
     * @param baseUrl The base URL for the API
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
     * Creates the FormService from a Retrofit instance.
     * 
     * @param retrofit The Retrofit instance
     * @return FormService implementation
     */
    fun createService(retrofit: Retrofit): FormService {
        return retrofit.create(FormService::class.java)
    }
    
    /**
     * Helper to create a multipart file part.
     * 
     * @param name The field name
     * @param filename The filename
     * @param content The file content as bytes
     * @return MultipartBody.Part for the file
     */
    fun createFilePart(name: String, filename: String, content: ByteArray): MultipartBody.Part {
        val requestBody = content.toRequestBody()
        return MultipartBody.Part.createFormData(name, filename, requestBody)
    }
}

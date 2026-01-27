package com.example.retrofitdemo.model

import com.squareup.moshi.Json

/**
 * LoginRequest model for form-encoded login endpoint.
 */
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

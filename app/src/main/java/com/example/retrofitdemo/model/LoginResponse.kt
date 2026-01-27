package com.example.retrofitdemo.model

import com.squareup.moshi.Json

/**
 * LoginResponse model for login endpoint response.
 */
data class LoginResponse(
    @Json(name = "token") val token: String,
    @Json(name = "userId") val userId: Int
)

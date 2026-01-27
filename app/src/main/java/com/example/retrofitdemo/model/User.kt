package com.example.retrofitdemo.model

import com.squareup.moshi.Json

/**
 * User model representing a user from the API.
 * 
 * Demonstrates Moshi annotations:
 * - @Json: Maps JSON field names to Kotlin property names
 * - data class: Automatically generates equals, hashCode, toString, copy
 */
data class User(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "username") val username: String? = null
)

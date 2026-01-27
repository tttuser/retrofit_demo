package com.example.retrofitdemo.model

import com.squareup.moshi.Json

/**
 * Post model representing a blog post from the API.
 */
data class Post(
    @Json(name = "id") val id: Int,
    @Json(name = "userId") val userId: Int,
    @Json(name = "title") val title: String,
    @Json(name = "body") val body: String
)

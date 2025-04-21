package com.example.explorelens.data.network

import com.google.gson.annotations.SerializedName

data class SiteDetails(
    @SerializedName("_id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("averageRating") val averageRating: Float = 0f,
    @SerializedName("ratingCount") val ratingCount: Int = 0,
    @SerializedName("comments") val comments: List<Comment> = emptyList()
)

data class Comment(
    @SerializedName("user") val user: String,
    @SerializedName("content") val content: String,
    @SerializedName("date") val date: String?
)
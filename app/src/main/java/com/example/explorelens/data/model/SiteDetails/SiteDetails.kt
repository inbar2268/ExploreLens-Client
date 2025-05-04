package com.example.explorelens.data.model.SiteDetails

import com.google.gson.annotations.SerializedName

data class SiteDetails(
    @SerializedName("_id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("averageRating") val averageRating: Float = 0f,
    @SerializedName("ratingCount") val ratingCount: Int = 0,
    @SerializedName("imageUrl") val imageUrl: String? = null
)


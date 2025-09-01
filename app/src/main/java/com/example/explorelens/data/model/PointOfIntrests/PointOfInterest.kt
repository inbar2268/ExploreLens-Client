package com.example.explorelens.data.model.PointOfIntrests

import com.google.gson.annotations.SerializedName
import com.example.explorelens.data.db.places.Review


data class PointOfInterest(
    @SerializedName("place_id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("location") val location: GeoLocation,
    @SerializedName("rating") val rating: Float,
    @SerializedName("type") val type: String,
    @SerializedName("address") val address: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("business_status") val businessStatus: String,
    @SerializedName("opening_hours") val openingHours: OpeningHours,
    @SerializedName("elevation") val elevation: Double,
    @SerializedName("editorial_summary") val editorialSummary: String?,
    @SerializedName("website") val website: String?,
    @SerializedName("price_level") val priceLevel: Int?,
    @SerializedName("reviews") val reviews: List<Review>?
    )



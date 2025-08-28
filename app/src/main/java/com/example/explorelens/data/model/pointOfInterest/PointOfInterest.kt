package com.example.explorelens.data.model.pointOfInterest

import com.google.gson.annotations.SerializedName

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
    )



package com.example.explorelens.data.model.pointOfInterest

import com.google.gson.annotations.SerializedName

data class GeoLocation (
    @SerializedName("lat")  val lat: Double,
    @SerializedName("lng") val lng: Double,
)
package com.example.explorelens.data.model.PointOfIntrests

import com.google.gson.annotations.SerializedName

data class GeoLocation (
    @SerializedName("lat")  val lat: Double,
    @SerializedName("lng") val lng: Double,
)
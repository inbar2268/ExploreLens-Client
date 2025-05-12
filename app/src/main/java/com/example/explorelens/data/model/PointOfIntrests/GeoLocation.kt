package com.example.explorelens.data.model.PointOfIntrests

import com.google.gson.annotations.SerializedName

data class GeoLocation (
    @SerializedName("lat")  val lat: Float,
    @SerializedName("lng") val lng: Float,
)
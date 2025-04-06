package com.example.explorelens.data.network

import com.google.gson.annotations.SerializedName

data class SiteInformation(
    @SerializedName("label")
    var label: String = "",
    @SerializedName("x")
    var x: Float = 0f,
    @SerializedName("y")
    var y: Float = 0f,
    @SerializedName("siteName")
    var siteName: String = ""
)
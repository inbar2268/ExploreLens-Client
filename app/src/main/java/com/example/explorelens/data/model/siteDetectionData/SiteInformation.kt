package com.example.explorelens.data.model.siteDetectionData

import com.google.gson.annotations.SerializedName

data class SiteInformation(
    @SerializedName("id")
    var id: String = "",
    @SerializedName("label")
    var label: String = "",
    @SerializedName("x")
    var x: Float = 0f,
    @SerializedName("y")
    var y: Float = 0f,
    @SerializedName("siteName")
    var siteName: String = ""
)
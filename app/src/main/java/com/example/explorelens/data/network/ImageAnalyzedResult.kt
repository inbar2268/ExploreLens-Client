package com.example.explorelens.data.network

import com.google.gson.annotations.SerializedName

data class ImageAnalyzedResult(
    @SerializedName("status")
    var status: String = "",
    @SerializedName("description")
    var description: String = "",
    @SerializedName("siteInformation")
    var siteInformation: SiteInformation? = null
)

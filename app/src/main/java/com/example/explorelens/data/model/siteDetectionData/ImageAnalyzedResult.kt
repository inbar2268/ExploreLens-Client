package com.example.explorelens.data.model.siteDetectionData

import com.google.gson.annotations.SerializedName

data class ImageAnalyzedResult(
    @SerializedName("status")
    var status: String = "",
    @SerializedName("description")
    var description: String = "",
    @SerializedName("siteInformation")
    var siteInformation: SiteInformation? = null
)

package com.example.explorelens.networking

import com.example.explorelens.networking.ImageAnalyzedResult
import com.google.gson.annotations.SerializedName

data class allImageAnalyzedResults(
    val result: List<ImageAnalyzedResult>? = null,
    // For direct response format
    val status: String? = null,
    val description: String? = null,
    val siteInformation: SiteInformation? = null
)
package com.example.explorelens.data.network

data class allImageAnalyzedResults(
    val result: List<ImageAnalyzedResult>? = null,
    // For direct response format
    val status: String? = null,
    val description: String? = null,
    val siteInformation: SiteInformation? = null
)
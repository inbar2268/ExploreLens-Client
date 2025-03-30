package com.example.explorelens.networking

import com.example.explorelens.networking.ImageAnalyzedResult
import com.google.gson.annotations.SerializedName

class allImageAnalyzedResults(
    @SerializedName("objects")
    val result: List<ImageAnalyzedResult>,
)
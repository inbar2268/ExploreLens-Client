package com.example.explorelens.networking

import com.example.explorelens.networking.boundingBox
import com.example.explorelens.networking.center
import com.google.gson.annotations.SerializedName

class ImageAnalyzedResult(
    @SerializedName("labels")
    var label: String = "",
    @SerializedName("boundingBox")
    var boundingBox: boundingBox? = null,
    @SerializedName("center")
    var center: center? = null,
)

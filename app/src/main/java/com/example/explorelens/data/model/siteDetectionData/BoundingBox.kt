package com.example.explorelens.data.model.siteDetectionData

import com.google.gson.annotations.SerializedName

class boundingBox (
    @SerializedName("x")
    var x: Float = 0f,
    @SerializedName("y")
    var y: Float = 0f,
    @SerializedName("width")
    var width: Float = 0f,
    @SerializedName("height")
    var height: Float = 0f,

    )
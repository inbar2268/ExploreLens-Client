package com.example.explorelens.data.model.comments

import com.google.gson.annotations.SerializedName

data class Review(
    @SerializedName("_id")  val _id: String,
    @SerializedName("userId") val user: String,
    @SerializedName("content") val content: String,
    @SerializedName("date") val date: String?
)


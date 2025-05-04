package com.example.explorelens.data.model.comments

import com.google.gson.annotations.SerializedName

data class Comment(
    @SerializedName("_id")  val _id: String,
    @SerializedName("owner") val user: String,
    @SerializedName("content") val content: String,
    @SerializedName("date") val date: String?
)


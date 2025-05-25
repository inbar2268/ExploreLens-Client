package com.example.explorelens.data.model

data class UserResponse(
    val _id: String,
    val username: String,
    val email: String,
    val profilePicture: String?
)

data class UpdateUserRequest(
    val username: String,
    val email: String? = null,
    val profilePicture: String? = null
)
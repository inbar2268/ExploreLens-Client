package com.example.explorelens.data.network.user

import com.example.explorelens.data.model.UserResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface UserApi {
    @GET("users/{id}")
    suspend fun getUserById(@Path("id") userId: String): Response<UserResponse>
}
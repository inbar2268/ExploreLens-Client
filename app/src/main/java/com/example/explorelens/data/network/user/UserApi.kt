package com.example.explorelens.data.network.user

import com.example.explorelens.data.model.UpdateUserRequest
import com.example.explorelens.data.model.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserApi {
    @GET("users/{id}")
    suspend fun getUserById(@Path("id") userId: String): Response<UserResponse>

    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") userId: String,
        @Body updateUserRequest: UpdateUserRequest
    ): Response<UserResponse>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") userId: String): Response<Void>
}
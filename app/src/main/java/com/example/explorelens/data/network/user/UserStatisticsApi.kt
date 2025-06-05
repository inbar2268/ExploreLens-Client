package com.example.explorelens.data.network.user

import com.example.explorelens.data.model.statistics.UserStatisticsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface UserStatisticsApi {
    @GET("/user_statistics/{userId}")
    suspend fun getUserStatistics(@Path("userId") userId: String): Response<UserStatisticsResponse>
}
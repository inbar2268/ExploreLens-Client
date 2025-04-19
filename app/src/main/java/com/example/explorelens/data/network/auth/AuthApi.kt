package com.example.explorelens.data.network.auth

import com.example.explorelens.data.model.*
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Response


interface AuthApi {
    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/auth/google")
    suspend fun googleSignIn(@Body request: GoogleSignInRequest): Response<LoginResponse>

    @POST("/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<LoginResponse>

    @POST("/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>
}
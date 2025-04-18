package com.example.explorelens.data.network.auth

import com.example.explorelens.data.model.*
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/auth/register")
    fun register(@Body request: RegisterRequest): Call<RegisterResponse>

    @POST("/auth/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("/auth/google")
    fun googleSignIn(@Body request: GoogleSignInRequest): Call<LoginResponse>

    @POST("/auth/refresh")
    fun refreshToken(@Body request: RefreshTokenRequest): Call<LoginResponse>

    @POST("/auth/logout")
    fun logout(@Body request: LogoutRequest): Call<Unit>
}
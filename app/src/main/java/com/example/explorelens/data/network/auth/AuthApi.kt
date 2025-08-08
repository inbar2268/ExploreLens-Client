package com.example.explorelens.data.network.auth

import com.example.explorelens.data.model.auth.ChangePasswordRequest
import com.example.explorelens.data.model.auth.ForgotPasswordRequest
import com.example.explorelens.data.model.auth.GoogleSignInRequest
import com.example.explorelens.data.model.auth.LoginRequest
import com.example.explorelens.data.model.auth.LoginResponse
import com.example.explorelens.data.model.auth.LogoutRequest
import com.example.explorelens.data.model.auth.RefreshTokenRequest
import com.example.explorelens.data.model.auth.RegisterRequest
import com.example.explorelens.data.model.auth.ResetPasswordRequest
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.Response

interface AuthApi {
    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("/auth/google")
    suspend fun googleSignIn(@Body request: GoogleSignInRequest): Response<LoginResponse>

    @POST("/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<LoginResponse>

    @POST("/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @POST("auth/forgot")
    suspend fun forgotPassword(@Body forgotPasswordRequest: ForgotPasswordRequest): Response<Unit>

    @POST("auth/reset")
    suspend fun resetPassword(@Body resetPasswordRequest: ResetPasswordRequest): Response<Unit>

    @POST("/auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body request: ChangePasswordRequest
    ): Response<Unit>
}
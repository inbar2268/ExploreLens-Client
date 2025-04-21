package com.example.explorelens.data.model


data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val accessToken: String, val refreshToken: String, val _id: String)

data class RegisterRequest(val name: String, val email: String, val password: String, val profilePicture: String)

data class GoogleSignInRequest(val credential: String)

data class RefreshTokenRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String)

data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val token: String, val newPassword: String)
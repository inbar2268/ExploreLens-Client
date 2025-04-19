package com.example.explorelens.data.model

data class RegisterRequest(val username: String, val email: String, val password: String)
data class RegisterResponse(val _id: String, val username: String, val email: String, val accessToken: String, val refreshToken: String)

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val accessToken: String, val refreshToken: String, val _id: String)

data class GoogleSignInRequest(val credential: String)

data class RefreshTokenRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String)
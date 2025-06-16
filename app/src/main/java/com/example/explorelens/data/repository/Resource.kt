package com.example.explorelens.data.repository

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null,
    val isFromCache: Boolean = false
) {
    class Success<T>(data: T, isFromCache: Boolean = false) : Resource<T>(data = data, isFromCache = isFromCache)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data = data, message = message)
    class Loading<T>(data: T? = null) : Resource<T>(data = data)
}
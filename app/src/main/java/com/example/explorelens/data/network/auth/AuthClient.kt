package com.example.explorelens.data.network.auth

import android.content.Context
import com.example.explorelens.BuildConfig
import com.example.explorelens.data.network.user.UserApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val BASE_URL = BuildConfig.BASE_URL

object AuthClient {
    private var authInterceptor: AuthInterceptor? = null

    // Initialize with application context
    fun init(context: Context) {
        authInterceptor = AuthInterceptor(context)
    }

    private val defaultOkHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // OkHttpClient with auth interceptor for authenticated API calls
    private val authenticatedOkHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE

        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        // Add auth interceptor if available
        authInterceptor?.let { builder.addInterceptor(it) }

        builder.build()
    }

    // Use default client for auth API (no interceptor)
    val authApi: AuthApi by lazy {
        createRetrofitClient(defaultOkHttpClient).create(AuthApi::class.java)
    }

    // Use authenticated client for user API
    val userApi: UserApi by lazy {
        createRetrofitClient(authenticatedOkHttpClient).create(UserApi::class.java)
    }

    private fun createRetrofitClient(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
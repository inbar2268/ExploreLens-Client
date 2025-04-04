package com.example.explorelens.networking

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val BASE_URL = "http://10.100.102.69:3000"

object AnalyzedResultsClient {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AnalyzedResultInterceptor())
            .build()
    }

    val analyzedResultApiClient: AnalyzedResultApi by lazy {
        val retrofitClient = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofitClient.create(AnalyzedResultApi::class.java)
    }
    val siteDetailsApiClient: SiteDetailsApi by lazy {
        createRetrofitClient().create(SiteDetailsApi::class.java)
    }
    private fun createRetrofitClient(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

}
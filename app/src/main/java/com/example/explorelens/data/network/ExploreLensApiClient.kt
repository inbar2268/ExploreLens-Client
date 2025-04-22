package com.example.explorelens.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.explorelens.BuildConfig
import com.example.explorelens.data.network.detectionResult.AnalyzedResultApi
import com.example.explorelens.data.network.detectionResult.AnalyzedResultInterceptor
import com.example.explorelens.data.network.siteDetails.SiteDetailsApi

val BASE_URL = BuildConfig.BASE_URL

object AnalyzedResultsClient {
    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY // Log body content

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) // Add the logging interceptor
            .addInterceptor(AnalyzedResultInterceptor())
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // Set connection timeout
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // Set read timeout
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // Set write timeout
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
    val siteCommentsApiClient: CommentsApi by lazy {
        createRetrofitClient().create(CommentsApi::class.java)
    }

    private fun createRetrofitClient(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

package com.example.explorelens.data.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.explorelens.BuildConfig
import com.example.explorelens.data.network.auth.AuthApi
import com.example.explorelens.data.network.auth.AuthInterceptor
import com.example.explorelens.data.network.detectionResult.AnalyzedResultApi
import com.example.explorelens.data.network.site.SiteHistoryApi
import com.example.explorelens.data.network.siteDetails.SiteDetailsApi
import com.example.explorelens.data.network.user.UserApi
import java.util.concurrent.TimeUnit


object ExploreLensApiClient {

    private val BASE_URL = BuildConfig.BASE_URL
    private lateinit var authInterceptor: AuthInterceptor
    private val commonHeadersInterceptor = CommonHeadersInterceptor()

    fun init(context: Context) {
        authInterceptor = AuthInterceptor(context)
    }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
    }

    private val publicOkHttpClient: OkHttpClient by lazy {
        createBaseOkHttpClientBuilder()
            .build()
    }

    private val authenticatedOkHttpClient: OkHttpClient by lazy {
        createBaseOkHttpClientBuilder()
            .addInterceptor(authInterceptor)
            .build()
    }

    private fun createBaseOkHttpClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(commonHeadersInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
    }

    private fun createRetrofitClient(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy {
        createRetrofitClient(publicOkHttpClient).create(AuthApi::class.java)
    }

    val userApi: UserApi by lazy {
        createRetrofitClient(authenticatedOkHttpClient).create(UserApi::class.java)
    }

    val siteHistoryApi: SiteHistoryApi by lazy {
        createRetrofitClient(authenticatedOkHttpClient).create(SiteHistoryApi::class.java)
    }

    val analyzedResultApi: AnalyzedResultApi by lazy {
        createRetrofitClient(authenticatedOkHttpClient).create(AnalyzedResultApi::class.java)
    }

    val siteDetailsApi: SiteDetailsApi by lazy {
        createRetrofitClient(authenticatedOkHttpClient).create(SiteDetailsApi::class.java)
    }

    val reviewsApi: ReviewsApi by lazy {
        createRetrofitClient(authenticatedOkHttpClient).create(ReviewsApi::class.java)
    }

    val nearbyPlacesApi: NearbyPlacesApi by lazy {
        createRetrofitClient(authenticatedOkHttpClient).create(NearbyPlacesApi::class.java)
    }
}

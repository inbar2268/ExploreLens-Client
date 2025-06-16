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
import com.example.explorelens.data.network.ChatApi
import com.example.explorelens.data.network.user.UserStatisticsApi
import java.util.concurrent.TimeUnit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

object ExploreLensApiClient {

    private val BASE_URL = BuildConfig.BASE_URL
    private const val OPENAI_BASE_URL = "https://api.openai.com/"
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

    private fun getOkHttpClient(authenticated: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(commonHeadersInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (authenticated) {
            builder.addInterceptor(authInterceptor)
        }

        return builder.build()
    }

    private fun createRetrofitClient(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }


    val authApi: AuthApi by lazy {
        createRetrofitClient(getOkHttpClient(authenticated = false)).create(AuthApi::class.java)
    }

    val userApi: UserApi by lazy {
        createRetrofitClient(getOkHttpClient(authenticated = true)).create(UserApi::class.java)
    }

    val siteHistoryApi: SiteHistoryApi by lazy {
        createRetrofitClient(getOkHttpClient(authenticated = true)).create(SiteHistoryApi::class.java)
    }

    val analyzedResultApi: AnalyzedResultApi by lazy {
        createRetrofitClient(getOkHttpClient(authenticated = true)).create(AnalyzedResultApi::class.java)
    }

    val siteDetailsApi: SiteDetailsApi by lazy {
        createRetrofitClient(getOkHttpClient(authenticated = true)).create(SiteDetailsApi::class.java)
    }

    val reviewsApi: ReviewsApi by lazy {
        createRetrofitClient(getOkHttpClient(authenticated = true)).create(ReviewsApi::class.java)
    }

    val nearbyPlacesApi: NearbyPlacesApi by lazy {
        createRetrofitClient(getOkHttpClient(authenticated = true)).create(NearbyPlacesApi::class.java)
    }

    val chatApi: ChatApi by lazy {
        Retrofit.Builder()
            .baseUrl(OPENAI_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
    }

    val userStatisticsApi: UserStatisticsApi by lazy {
        createRetrofitClient(getOkHttpClient(authenticated = true)).create(UserStatisticsApi::class.java)
    }
}

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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

object ExploreLensApiClient {

    private val BASE_URL = BuildConfig.BASE_URL
    private lateinit var authInterceptor: AuthInterceptor
    private val commonHeadersInterceptor = CommonHeadersInterceptor()

    private val USE_UNSAFE_CLIENT = true

    fun init(context: Context) {
        authInterceptor = AuthInterceptor(context)
    }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
    }

    private fun createBaseOkHttpClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(commonHeadersInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .hostnameVerifier(HostnameVerifier { hostname, _ ->
                hostname == "explorelensserver.cs.colman.ac.il"
            })
    }

    fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .addInterceptor(loggingInterceptor)
                .addInterceptor(commonHeadersInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    // Helper function to get the appropriate OkHttpClient based on flag and interceptor usage
    private fun getOkHttpClient(authenticated: Boolean): OkHttpClient {
        return if (USE_UNSAFE_CLIENT) {
            // For unsafe, just add auth interceptor if needed
            if (authenticated) {
                getUnsafeOkHttpClient().newBuilder()
                    .addInterceptor(authInterceptor)
                    .build()
            } else {
                getUnsafeOkHttpClient()
            }
        } else {
            // Safe client
            if (authenticated) {
                createBaseOkHttpClientBuilder()
                    .addInterceptor(authInterceptor)
                    .build()
            } else {
                createBaseOkHttpClientBuilder()
                    .build()
            }
        }
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
}

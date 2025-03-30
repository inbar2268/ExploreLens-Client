import com.example.explorelens.networking.AnalyzedResultApi
import com.example.explorelens.networking.AnalyzedResultInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val BASE_URL = "http://192.168.68.132:3000"

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
}
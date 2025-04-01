package com.example.explorelens

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.example.explorelens.ml.R
import java.util.concurrent.TimeUnit



class DetailActivity : AppCompatActivity() {
    private lateinit var labelTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var loadingIndicator: ProgressBar
// Add this in onCreate
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        labelTextView = findViewById<TextView>(R.id.labelTextView)
        descriptionTextView = findViewById(R.id.descriptionTextView)

        // Set label to TextView
        val label = intent.getStringExtra("LABEL_KEY") ?: "Unknown"
        Log.d("DetailActivity", "Received label: $label")

        labelTextView.text = label
        loadingIndicator = findViewById(R.id.loadingIndicator)
        loadingIndicator.visibility = View.VISIBLE
        fetchSiteDetails(label)

    }
    private fun loadData() {
        Handler(Looper.getMainLooper()).postDelayed({
            // Once data is loaded, update UI and hide loading indicator
            labelTextView.text = "Your Title"
            descriptionTextView.text = "Your description content here"

            // Hide loading indicator
            loadingIndicator.visibility = View.GONE
        }, 1500) // Simulating a 1.5 second load time
    }
    private fun fetchSiteDetails(label: String) {
        // Create Retrofit instance with a base URL
        val baseUrl = "http://10.100.102.69:3000" // Use your server URL here
        val labelWithoutSpaces = label.replace(" ", "")

        // Create OkHttpClient with interceptor for headers
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        // Build Retrofit client
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // Create the API service
        val apiService = retrofit.create(SiteDetailsApiService::class.java)

        // Make the API call
        val call = apiService.getSiteDetails(labelWithoutSpaces)
        Log.d("DetailActivity", "Sending request with label: $labelWithoutSpaces")

        call.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful) {
                    loadingIndicator.visibility = View.GONE
                    val responseBody = response.body()
                    if (responseBody != null) {
                        // Update UI with the string response
                        descriptionTextView.text = responseBody
                    } else {
                        Log.e("DetailActivity", "Response body is null")
                        showError("No data returned from server")
                    }
                } else {
                    Log.e("DetailActivity", "Error: ${response.code()}")
                    showError("Failed to load details: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                loadingIndicator.visibility = View.GONE
                Log.e("DetailActivity", "Network error: ${t.message}", t)
                showError("Network error: ${t.message}")
            }
        })
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // API Service interface (defined inside the Activity for self-containment)
    private interface SiteDetailsApiService {
        @GET("/site-info/site-details")
        fun getSiteDetails(@Query("siteName") siteName: String): Call<String>
    }

    // Data class for the response (defined inside the Activity for self-containment)
    data class SiteDetailsResponse(
        @SerializedName("description") val description: String = "",
        // Add any other fields your server returns
    )
}

// Update DetailActivity.kt
package com.example.explorelens

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.explorelens.data.network.AnalyzedResultsClient
import com.example.explorelens.R
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DetailActivity : AppCompatActivity() {
    private lateinit var labelTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var loadingIndicator: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        // Initialize views
        labelTextView = findViewById(R.id.labelTextView)
        descriptionTextView = findViewById(R.id.descriptionTextView)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        // Set label to TextView
        val label = intent.getStringExtra("LABEL_KEY") ?: "Unknown"
        Log.d("DetailActivity", "Received label: $label")

        labelTextView.text = label
        loadingIndicator.visibility = View.VISIBLE

        // Fetch site details
        fetchSiteDetails(label)
    }

    private fun fetchSiteDetails(label: String) {
        val labelWithoutSpaces = label.replace(" ", "")
        Log.d("DetailActivity", "Sending request with label: $labelWithoutSpaces")

        // Use the client from our networking package
        val call = AnalyzedResultsClient.siteDetailsApiClient.getSiteDetails(labelWithoutSpaces)

        call.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                loadingIndicator.visibility = View.GONE

                if (response.isSuccessful) {
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
}
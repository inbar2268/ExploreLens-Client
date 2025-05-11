package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.example.explorelens.data.model.SiteDetails.SiteDetailsRatingRequest
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SiteDetailsRepository(context: Context) {

    private val TAG = "SiteDetailsRepository"
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)

    // Add a cache to avoid repeated API calls
    private val siteDetailsCache = mutableMapOf<String, SiteDetails>()

    // Get site details using LiveData - this is the new method to fetch site details
    // In your SiteDetailsRepository.kt - update the getSiteDetails method
    fun getSiteDetails(siteId: String): LiveData<SiteDetails?> {
        val result = MutableLiveData<SiteDetails?>()

        // Safety check for null or empty siteId
        if (siteId.isNullOrBlank()) {
            Log.e(TAG, "getSiteDetails called with null or empty siteId")
            result.postValue(null)
            return result
        }

        // Check cache first
        siteDetailsCache[siteId]?.let {
            result.postValue(it)
            return result
        }

        // Clean the site ID by removing spaces
        val cleanSiteId = siteId.replace(" ", "")

        // Safety check for clean siteId
        if (cleanSiteId.isBlank()) {
            Log.e(TAG, "Clean siteId is blank for original: $siteId")
            result.postValue(null)
            return result
        }

        Log.d(TAG, "Fetching site details for: $cleanSiteId")

        // Fetch from API if not in cache
        ExploreLensApiClient.siteDetailsApi.getSiteDetails(cleanSiteId).enqueue(object : Callback<SiteDetails> {
            override fun onResponse(call: Call<SiteDetails>, response: Response<SiteDetails>) {
                if (response.isSuccessful) {
                    response.body()?.let { siteDetails ->
                        // Cache the result
                        siteDetailsCache[siteId] = siteDetails
                        result.postValue(siteDetails)
                        Log.d(TAG, "Successfully loaded site details for: $siteId")
                    } ?: run {
                        Log.e(TAG, "No site details returned for $siteId")
                        result.postValue(null)
                    }
                } else {
                    Log.e(TAG, "Failed to fetch site details: ${response.code()} - ${response.message()}")
                    result.postValue(null)
                }
            }

            override fun onFailure(call: Call<SiteDetails>, t: Throwable) {
                Log.e(TAG, "Error fetching site details for $siteId", t)
                result.postValue(null)
            }
        })

        return result
    }

    // Your existing addRating method
    suspend fun addRating(siteId: String, rating: Float): Result<SiteDetails> {
        val userId = tokenManager.getUserId()
        val request = userId?.let { SiteDetailsRatingRequest(rating,it) }

        return try {
            val response: Response<SiteDetails>? =
                request?.let { ExploreLensApiClient.siteDetailsApi.addRating(siteId, it) }

            if (response?.isSuccessful == true) {
                response.body()?.let {
                    Log.d("SiteDetailsRepository", "rating added successfully: $it")
                    // Update cache when adding a rating
                    siteDetailsCache[siteId] = it
                    Result.success(it)
                } ?: run {
                    Log.e("SiteDetailsRepository", "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response?.errorBody()?.string()
                val errorMessage = "Error ${response?.code()}: ${errorBody ?: "Unknown error"}"
                Log.e("SiteDetailsRepository", errorMessage)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e("SiteDetailsRepository", "Network error: ${e.localizedMessage}", e)
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }

    // Format a site ID to be more readable (helper method)
    fun formatSiteId(siteInfoId: String): String {
        return siteInfoId.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }
}
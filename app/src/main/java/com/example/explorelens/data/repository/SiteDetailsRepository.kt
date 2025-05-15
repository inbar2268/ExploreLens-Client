package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.example.explorelens.data.model.SiteDetails.SiteDetailsRatingRequest
import com.example.explorelens.data.model.comments.Review
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SiteDetailsRepository(context: Context) {
    private val TAG = "SiteDetailsRepository"
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)

    // Cache for site details
    private val siteDetailsCache = mutableMapOf<String, SiteDetails>()

    suspend fun addRating(siteId: String, rating: Float): Result<SiteDetails> {
        val userId = tokenManager.getUserId()
        val request = userId?.let { SiteDetailsRatingRequest(rating,it) }

        return try {
            val response: Response<SiteDetails>? =
                request?.let { ExploreLensApiClient.siteDetailsApi.addRating(siteId, it) }

            if (response?.isSuccessful == true) {
                response.body()?.let {
                    Log.d("SiteDetailsRepository", "rating added successfully: $it")
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

    // New method to get site details as LiveData
    fun getSiteDetailsLiveData(siteId: String): LiveData<SiteDetails?> {
        val result = MutableLiveData<SiteDetails?>()

        // Check cache first
        siteDetailsCache[siteId]?.let {
            result.postValue(it)
            return result
        }

        // Clean the site ID
        val cleanSiteId = siteId.replace(" ", "")
        if (cleanSiteId.isBlank()) {
            result.postValue(null)
            return result
        }

        // Fetch from API
        ExploreLensApiClient.siteDetailsApi.getSiteDetails(cleanSiteId)
            .enqueue(object : Callback<SiteDetails> {
                override fun onResponse(call: Call<SiteDetails>, response: Response<SiteDetails>) {
                    if (response.isSuccessful) {
                        val siteDetails = response.body()
                        if (siteDetails != null) {
                            // Update cache
                            siteDetailsCache[siteId] = siteDetails
                            result.postValue(siteDetails)
                        } else {
                            result.postValue(null)
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch site details: ${response.code()}")
                        result.postValue(null)
                    }
                }

                override fun onFailure(call: Call<SiteDetails>, t: Throwable) {
                    Log.e(TAG, "Error fetching site details", t)
                    result.postValue(null)
                }
            })

        return result
    }


    // Add a method that returns a direct callback for immediate use
    fun fetchSiteDetails(
        siteId: String,
        onSuccess: (SiteDetails) -> Unit,
        onError: () -> Unit
    ) {
        // Check cache first
        siteDetailsCache[siteId]?.let {
            onSuccess(it)
            return
        }

        // Clean the site ID
        val cleanSiteId = siteId.replace(" ", "")
        if (cleanSiteId.isBlank()) {
            onError()
            return
        }

        // Fetch from API
        ExploreLensApiClient.siteDetailsApi.getSiteDetails(cleanSiteId)
            .enqueue(object : Callback<SiteDetails> {
                override fun onResponse(call: Call<SiteDetails>, response: Response<SiteDetails>) {
                    if (response.isSuccessful) {
                        val siteDetails = response.body()
                        if (siteDetails != null) {
                            // Update cache
                            siteDetailsCache[siteId] = siteDetails
                            onSuccess(siteDetails)
                        } else {
                            onError()
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch site details: ${response.code()}")
                        onError()
                    }
                }

                override fun onFailure(call: Call<SiteDetails>, t: Throwable) {
                    Log.e(TAG, "Error fetching site details", t)
                    onError()
                }
            })
    }

    // Helper to format a site ID
    fun formatSiteId(siteInfoId: String): String {
        return siteInfoId.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }
}
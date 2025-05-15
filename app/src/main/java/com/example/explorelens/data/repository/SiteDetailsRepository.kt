package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.example.explorelens.data.model.SiteDetails.SiteDetailsRatingRequest
import com.example.explorelens.data.model.comments.Review
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import retrofit2.Response

class SiteDetailsRepository(context: Context) {

    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)
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

    suspend fun fetchSiteDetails(siteId: String): Result<SiteDetails> {
        return try {
            val response = ExploreLensApiClient.siteDetailsApi.getSiteDetails(siteId)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response body"))
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }
}
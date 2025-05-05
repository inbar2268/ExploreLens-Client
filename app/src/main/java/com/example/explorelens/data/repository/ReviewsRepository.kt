package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import com.example.explorelens.data.model.comments.Review
import com.example.explorelens.data.model.comments.ReviewRequest
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import retrofit2.Response

class ReviewsRepository(context: Context) {
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)
    suspend fun createReview(siteId: String, content: String): Result<Review> {
        val userId = tokenManager.getUserId()
        val request = userId?.let { ReviewRequest(it, content) }

        return try {
            val response: Response<Review>? =
                request?.let { ExploreLensApiClient.reviewsApi.createReview(siteId, it) }

            if (response?.isSuccessful == true) {
                response.body()?.let {
                    Log.d("ReviewsRepository", "Comment created successfully: $it")
                    Result.success(it)
                } ?: run {
                    Log.e("ReviewsRepository", "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response?.errorBody()?.string()
                val errorMessage = "Error ${response?.code()}: ${errorBody ?: "Unknown error"}"
                Log.e("ReviewsRepository", errorMessage)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e("ReviewsRepository", "Network error: ${e.localizedMessage}", e)
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }

    suspend fun fetchSiteReviews(siteId: String): Result<List<Review>> {
        return try {
            val response = ExploreLensApiClient.reviewsApi.getSiteReviews(siteId)
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
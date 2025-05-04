package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import com.example.explorelens.data.model.comments.Comment
import com.example.explorelens.data.model.comments.CommentRequest
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import retrofit2.Response

class CommentsRepository(context: Context) {
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)
    suspend fun createComment(siteId: String, content: String): Result<Comment> {
        val userId = tokenManager.getUserId()
        val request = userId?.let { CommentRequest(it, content) }

        return try {
            val response: Response<Comment>? =
                request?.let { ExploreLensApiClient.commentsApi.createComment(siteId, it) }

            if (response?.isSuccessful == true) {
                response.body()?.let {
                    Log.d("CommentsRepository", "Comment created successfully: $it")
                    Result.success(it)
                } ?: run {
                    Log.e("CommentsRepository", "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response?.errorBody()?.string()
                val errorMessage = "Error ${response?.code()}: ${errorBody ?: "Unknown error"}"
                Log.e("CommentsRepository", errorMessage)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e("CommentsRepository", "Network error: ${e.localizedMessage}", e)
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }

    suspend fun fetchSiteComments(siteId: String): Result<List<Comment>> {
        return try {
            val response = ExploreLensApiClient.commentsApi.getSiteComments(siteId)
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
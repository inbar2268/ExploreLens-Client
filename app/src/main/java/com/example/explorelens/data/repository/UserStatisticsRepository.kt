package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.db.statistics.UserStatistics
import com.example.explorelens.data.model.statistics.UserStatisticsResponse
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserStatisticsRepository(context: Context) {
    private val TAG = "UserStatisticsRepository"
    private val userStatisticsDao = AppDatabase.getInstance(context).userStatisticsDao()
    private val userStatisticsApi = ExploreLensApiClient.userStatisticsApi
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)

    // Get user statistics from local database
    fun getUserStatistics(userId: String): LiveData<UserStatistics?> {
        return userStatisticsDao.getUserStatistics(userId)
    }

    // Fetch user statistics from server and cache in Room
    suspend fun fetchAndCacheUserStatistics(userId: String): Result<UserStatistics> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching user statistics for userId: $userId")

                val response = userStatisticsApi.getUserStatistics(userId)

                if (response.isSuccessful) {
                    val statisticsResponse = response.body()
                    if (statisticsResponse != null) {
                        Log.d(TAG, "Successfully fetched user statistics: $statisticsResponse")

                        // Convert API response to Room entity
                        val userStatistics = UserStatistics(
                            userId = statisticsResponse.userId,
                            percentageVisited = statisticsResponse.percentageVisited,
                            countryCount = statisticsResponse.countryCount,
                            continents = statisticsResponse.continents,
                            siteCount = statisticsResponse.siteCount,
                            countries = statisticsResponse.countries,
                            createdAt = System.currentTimeMillis()
                        )

                        // Save to local database
                        userStatisticsDao.insertUserStatistics(userStatistics)

                        Result.success(userStatistics)
                    } else {
                        Log.e(TAG, "Response body is null")
                        Result.failure(Exception("No statistics data received"))
                    }
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Failed to fetch user statistics: ${response.code()} - $errorMsg")
                    Result.failure(Exception("Failed to fetch statistics: $errorMsg"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while fetching user statistics", e)
                Result.failure(e)
            }
        }
    }

    // Get current user's statistics
    suspend fun getCurrentUserStatistics(): Result<UserStatistics> {
        val userId = tokenManager.getUserId()
        return if (userId != null) {
            // Try to get from local database first
            val localStats = userStatisticsDao.getUserStatisticsSync(userId)
            if (localStats != null) {
                // Check if data is recent (less than 1 hour old)
                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
                if (localStats.createdAt > oneHourAgo) {
                    Log.d(TAG, "Using cached statistics")
                    Result.success(localStats)
                } else {
                    // Data is old, fetch new data
                    Log.d(TAG, "Cached data is old, fetching new statistics")
                    fetchAndCacheUserStatistics(userId)
                }
            } else {
                // No local data, fetch from server
                Log.d(TAG, "No cached data, fetching from server")
                fetchAndCacheUserStatistics(userId)
            }
        } else {
            Result.failure(Exception("User ID not found"))
        }
    }

    // Clear user statistics
    suspend fun clearUserStatistics(userId: String) {
        withContext(Dispatchers.IO) {
            userStatisticsDao.deleteUserStatisticsByUserId(userId)
        }
    }
}
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

    // Get current user's statistics - ALWAYS syncs with server
    suspend fun getCurrentUserStatistics(): Result<UserStatistics> {
        val userId = tokenManager.getUserId()
        return if (userId != null) {
            Log.d(TAG, "Always syncing statistics with server for userId: $userId")

            try {
                // Always fetch fresh data from server first
                val serverResult = fetchAndCacheUserStatistics(userId)

                if (serverResult.isSuccess) {
                    Log.d(TAG, "Successfully synced with server")
                    serverResult
                } else {
                    // If server fetch fails, fall back to cached data as last resort
                    Log.w(TAG, "Server sync failed, attempting to use cached data as fallback")
                    val localStats = withContext(Dispatchers.IO) {
                        userStatisticsDao.getUserStatisticsSync(userId)
                    }

                    if (localStats != null) {
                        Log.w(TAG, "Using cached statistics as fallback (last updated: ${localStats.createdAt})")
                        Result.success(localStats)
                    } else {
                        Log.e(TAG, "No cached data available and server sync failed")
                        serverResult // Return the original failure
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during sync, trying cached data", e)

                // If there's an exception, try cached data
                val localStats = withContext(Dispatchers.IO) {
                    userStatisticsDao.getUserStatisticsSync(userId)
                }

                if (localStats != null) {
                    Log.w(TAG, "Using cached statistics due to sync exception")
                    Result.success(localStats)
                } else {
                    Log.e(TAG, "No cached data available and sync failed with exception")
                    Result.failure(e)
                }
            }
        } else {
            Log.e(TAG, "User ID not found in token manager")
            Result.failure(Exception("User ID not found"))
        }
    }

    // Alternative method for getting cached statistics only (useful for offline scenarios)
    suspend fun getCachedUserStatistics(): Result<UserStatistics> {
        val userId = tokenManager.getUserId()
        return if (userId != null) {
            withContext(Dispatchers.IO) {
                val localStats = userStatisticsDao.getUserStatisticsSync(userId)
                if (localStats != null) {
                    Log.d(TAG, "Retrieved cached statistics")
                    Result.success(localStats)
                } else {
                    Log.w(TAG, "No cached statistics found")
                    Result.failure(Exception("No cached statistics available"))
                }
            }
        } else {
            Result.failure(Exception("User ID not found"))
        }
    }

    // Force refresh statistics (same as getCurrentUserStatistics but more explicit)
    suspend fun refreshUserStatistics(): Result<UserStatistics> {
        Log.d(TAG, "Force refreshing user statistics")
        return getCurrentUserStatistics()
    }

    // Clear user statistics
    suspend fun clearUserStatistics(userId: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Clearing cached statistics for userId: $userId")
            userStatisticsDao.deleteUserStatisticsByUserId(userId)
        }
    }

    // Check if cached data exists and when it was last updated
    suspend fun getCachedDataInfo(): Pair<Boolean, Long?> {
        val userId = tokenManager.getUserId()
        return if (userId != null) {
            withContext(Dispatchers.IO) {
                val localStats = userStatisticsDao.getUserStatisticsSync(userId)
                if (localStats != null) {
                    Pair(true, localStats.createdAt)
                } else {
                    Pair(false, null)
                }
            }
        } else {
            Pair(false, null)
        }
    }
}

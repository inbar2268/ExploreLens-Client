package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.db.statistics.UserStatisticsEntity
import com.example.explorelens.data.model.statistics.UserStatisticsResponse
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class UserStatisticsRepository(context: Context) {

    companion object {
        private const val TAG = "UserStatisticsRepository"
        private const val CACHE_TIMEOUT_MINUTES = 15L // Cache valid for 15 minutes

        @Volatile
        private var INSTANCE: UserStatisticsRepository? = null

        fun getInstance(context: Context): UserStatisticsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserStatisticsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val userStatisticsDao = AppDatabase.getInstance(context).userStatisticsDao()
    private val userStatisticsApi = ExploreLensApiClient.userStatisticsApi
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)

    // Repository scope for background operations
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Get user statistics as LiveData (reactive, single source of truth)
     * This is the main method UI should observe
     */
    fun getUserStatisticsLiveData(): LiveData<Resource<UserStatisticsEntity>> {
        val userId = tokenManager.getUserId()

        return if (userId != null) {
            userStatisticsDao.getUserStatistics(userId).map { cachedData ->
                when {
                    cachedData == null -> Resource.Loading()
                    isCacheExpired(cachedData.createdAt) -> {
                        // Trigger refresh in background but return cached data
                        triggerBackgroundRefresh(userId)
                        Resource.Success(cachedData, isFromCache = true)
                    }
                    else -> Resource.Success(cachedData, isFromCache = false)
                }
            }
        } else {
            // Return error LiveData if no user ID
            androidx.lifecycle.MutableLiveData<Resource<UserStatisticsEntity>>().apply {
                value = Resource.Error("UserEntity not authenticated")
            }
        }
    }

    /**
     * Get user statistics as Flow (for more complex reactive scenarios)
     */
    fun getUserStatisticsFlow(): Flow<Resource<UserStatisticsEntity>> = flow {
        val userId = tokenManager.getUserId()

        if (userId == null) {
            emit(Resource.Error("UserEntity not authenticated"))
            return@flow
        }

        // Emit loading state
        emit(Resource.Loading())

        try {
            // First, emit cached data if available
            val cachedData = userStatisticsDao.getUserStatisticsSync(userId)
            if (cachedData != null) {
                emit(Resource.Success(cachedData, isFromCache = true))

                // If cache is fresh, we're done
                if (!isCacheExpired(cachedData.createdAt)) {
                    return@flow
                }
            }

            // Fetch fresh data from network
            val networkResult = fetchFromNetwork(userId)

            if (networkResult.isSuccess) {
                val freshData = networkResult.getOrThrow()
                // Cache the fresh data
                userStatisticsDao.insertUserStatistics(freshData)
                emit(Resource.Success(freshData, isFromCache = false))
            } else {
                // Network failed - if we have cached data, use it; otherwise emit error
                if (cachedData != null) {
                    emit(Resource.Success(cachedData, isFromCache = true))
                } else {
                    emit(Resource.Error(networkResult.exceptionOrNull()?.message ?: "Failed to load statistics"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in getUserStatisticsFlow", e)
            // Try to emit cached data as fallback
            val cachedData = userStatisticsDao.getUserStatisticsSync(userId)
            if (cachedData != null) {
                emit(Resource.Success(cachedData, isFromCache = true))
            } else {
                emit(Resource.Error(e.message ?: "Unknown error"))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Force refresh statistics from server
     */
    suspend fun refreshStatistics(): Resource<UserStatisticsEntity> {
        val userId = tokenManager.getUserId()

        return if (userId != null) {
            try {
                Log.d(TAG, "Force refreshing statistics for user: $userId")

                val networkResult = fetchFromNetwork(userId)

                if (networkResult.isSuccess) {
                    val freshData = networkResult.getOrThrow()
                    // Update cache
                    userStatisticsDao.insertUserStatistics(freshData)
                    Resource.Success(freshData, isFromCache = false)
                } else {
                    // Network failed - try cached data as fallback
                    val cachedData = withContext(Dispatchers.IO) {
                        userStatisticsDao.getUserStatisticsSync(userId)
                    }

                    if (cachedData != null) {
                        Log.w(TAG, "Network refresh failed, using cached data")
                        Resource.Success(cachedData, isFromCache = true)
                    } else {
                        Resource.Error(networkResult.exceptionOrNull()?.message ?: "Refresh failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during refresh", e)
                Resource.Error(e.message ?: "Refresh failed")
            }
        } else {
            Resource.Error("UserEntity not authenticated")
        }
    }

    /**
     * Get only cached statistics (useful for offline scenarios)
     */
    suspend fun getCachedStatistics(): Resource<UserStatisticsEntity> {
        val userId = tokenManager.getUserId()

        return if (userId != null) {
            withContext(Dispatchers.IO) {
                val cachedData = userStatisticsDao.getUserStatisticsSync(userId)
                if (cachedData != null) {
                    Resource.Success(cachedData, isFromCache = true)
                } else {
                    Resource.Error("No cached data available")
                }
            }
        } else {
            Resource.Error("UserEntity not authenticated")
        }
    }

    /**
     * Clear all cached statistics
     */
    suspend fun clearCache() {
        val userId = tokenManager.getUserId()
        if (userId != null) {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Clearing statistics cache for user: $userId")
                userStatisticsDao.deleteUserStatisticsByUserId(userId)
            }
        }
    }

    /**
     * Get cache info (exists, last updated)
     */
    suspend fun getCacheInfo(): CacheInfo {
        val userId = tokenManager.getUserId()

        return if (userId != null) {
            withContext(Dispatchers.IO) {
                val cachedData = userStatisticsDao.getUserStatisticsSync(userId)
                if (cachedData != null) {
                    CacheInfo(
                        exists = true,
                        lastUpdated = cachedData.createdAt,
                        isExpired = isCacheExpired(cachedData.createdAt)
                    )
                } else {
                    CacheInfo(exists = false, lastUpdated = null, isExpired = true)
                }
            }
        } else {
            CacheInfo(exists = false, lastUpdated = null, isExpired = true)
        }
    }

    // Private helper methods

    private suspend fun fetchFromNetwork(userId: String): Result<UserStatisticsEntity> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching statistics from network for user: $userId")

                val response = userStatisticsApi.getUserStatistics(userId)

                if (response.isSuccessful) {
                    val statisticsResponse = response.body()
                    if (statisticsResponse != null) {
                        val userStatistics = mapResponseToEntity(statisticsResponse)
                        Log.d(TAG, "Successfully fetched statistics from network")
                        Result.success(userStatistics)
                    } else {
                        Log.e(TAG, "Network response body is null")
                        Result.failure(Exception("Empty response from server"))
                    }
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                    Log.e(TAG, "Network request failed: $errorMsg")
                    Result.failure(Exception("Server error: $errorMsg"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network request exception", e)
                Result.failure(e)
            }
        }
    }

    private fun triggerBackgroundRefresh(userId: String) {
        // Use repository scope for background operations
        repositoryScope.launch {
            try {
                val networkResult = fetchFromNetwork(userId)
                if (networkResult.isSuccess) {
                    val freshData = networkResult.getOrThrow()
                    userStatisticsDao.insertUserStatistics(freshData)
                    Log.d(TAG, "Background refresh completed successfully")
                } else {
                    Log.w(TAG, "Background refresh failed: ${networkResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background refresh exception", e)
            }
        }
    }

    private fun mapResponseToEntity(response: UserStatisticsResponse): UserStatisticsEntity {
        return UserStatisticsEntity(
            userId = response.userId,
            percentageVisited = response.percentageVisited,
            countryCount = response.countryCount,
            siteCount = response.siteCount,
            countries = response.countries ?: emptyList(), // Handle null countries as fallback
            createdAt = System.currentTimeMillis()
        )
    }

    private fun isCacheExpired(cacheTimestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - cacheTimestamp
        val maxCacheAge = TimeUnit.MINUTES.toMillis(CACHE_TIMEOUT_MINUTES)
        return cacheAge > maxCacheAge
    }
}

/**
 * Cache information data class
 */
data class CacheInfo(
    val exists: Boolean,
    val lastUpdated: Long?,
    val isExpired: Boolean
)
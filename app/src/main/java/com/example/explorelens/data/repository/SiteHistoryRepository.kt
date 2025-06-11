package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.model.CreateSiteHistoryRequest
import com.example.explorelens.data.model.SiteHistoryItemResponse
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import com.google.gson.Gson


class SiteHistoryRepository(context: Context) {
    private val TAG = "SiteHistoryRepository"
    private val siteHistoryApi = ExploreLensApiClient.siteHistoryApi
    private val siteHistoryDao = AppDatabase.getInstance(context).siteHistoryDao()
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)

    fun getSiteHistoryByUserId(userId: String): LiveData<List<SiteHistory>> {
        return siteHistoryDao.getSiteHistoryByUserId(userId)
    }

    suspend fun createSiteHistory(
        siteInfoId: String,
        geohash: String,
        latitude: Double,
        longitude: Double,
    ) {

        val userId = tokenManager.getUserId()


        withContext(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

           userId?.let {
               val request = CreateSiteHistoryRequest(
                    siteInfoId = siteInfoId,
                    userId = it,
                    geohash = geohash,
                    latitude = latitude,
                    longitude = longitude,
                    createdAt = currentDate
               )
                try {
                    siteHistoryApi.createSiteHistory(request)

                } catch (e: Exception) {
                    // Handle error or retry later
                    e.printStackTrace()
                } finally {
                    val siteHistoryEntity = SiteHistory(
                        id = UUID.randomUUID().toString(),
                        siteInfoId = siteInfoId,
                        userId = userId,
                        latitude = latitude,
                        longitude = longitude,
                        geohash = geohash,
                        createdAt = System.currentTimeMillis()
                    )
                    siteHistoryDao.insertSiteHistory(siteHistoryEntity)
                }
           }
        }

    }
    /**
     * Reset site history for a specific user from both server and local database
     */
    suspend fun resetSiteHistoryForUser(userId: String): Result<Unit> {
        // Safety check for null or blank userId
        if (userId.isNullOrBlank()) {
            Log.e(TAG, "resetSiteHistoryForUser called with null or blank userId")
            return Result.failure(IllegalArgumentException("User ID cannot be null or blank"))
        }

        return try {
            Log.d(TAG, "Resetting site history for user: $userId")

            // First, get all site history for this user from local database
            val userSiteHistory = siteHistoryDao.getSiteHistoryByUserIdSync(userId)
            Log.d(TAG, "Found ${userSiteHistory.size} site history items to delete")

            if (userSiteHistory.isEmpty()) {
                Log.d(TAG, "No site history found for user")
                return Result.success(Unit)
            }

            // Delete each site history item from server using site info ID
            var successCount = 0
            var failureCount = 0

            userSiteHistory.forEach { siteHistory ->
                try {
                    val response = siteHistoryApi.deleteSiteHistoryById(siteHistory.id) // or use siteHistory.siteInfoId
                    if (response.isSuccessful) {
                        successCount++
                        Log.d(TAG, "Successfully deleted site history: ${siteHistory.id}")
                    } else {
                        failureCount++
                        Log.w(TAG, "Failed to delete site history: ${siteHistory.id}, code: ${response.code()}")
                    }
                } catch (e: Exception) {
                    failureCount++
                    Log.e(TAG, "Exception deleting site history: ${siteHistory.id}", e)
                }
            }

            // Clear local database regardless of server results
            siteHistoryDao.clearSiteHistoryForUser(userId)
            Log.d(TAG, "Local site history cleared successfully")

            // Report results
            if (failureCount == 0) {
                Log.d(TAG, "All site history deleted successfully from server ($successCount items)")
                Result.success(Unit)
            } else {
                Log.w(TAG, "Partial success: $successCount succeeded, $failureCount failed")
                Result.success(Unit) // Still success since local was cleared
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during site history reset", e)

            // If everything fails, still try to clear local
            try {
                siteHistoryDao.clearSiteHistoryForUser(userId)
                Log.d(TAG, "Local site history cleared despite server errors")
                Result.success(Unit)
            } catch (localException: Exception) {
                Result.failure(localException)
            }
        }
    }
    /**
     * Get site history count for user
     */
    suspend fun getSiteHistoryCount(userId: String): Int {
        return try {
            siteHistoryDao.getSiteHistoryCountForUser(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting site history count", e)
            0
        }
    }
    // Sync site history with server
    suspend fun syncSiteHistory(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                // Fetch history from the server
                val response = siteHistoryApi.getSitesHistoryByUserId(userId)
                if (response.isSuccessful) {
                    val serverHistoryItems = response.body() ?: emptyList()
                    val localHistoryItems = siteHistoryDao.getSiteHistoryByUserIdSync(userId)

                    // Get IDs from server and local DB for comparison
                    val serverItemIds = serverHistoryItems.map { it._id }
                    val localItemIds = localHistoryItems.map { it.id }

                    // Handle updates and new items
                    serverHistoryItems.forEach { serverItem ->
                        val localItem = localHistoryItems.find { it.id == serverItem._id }
                        if (localItem != null) {
                            // Update existing item
                            siteHistoryDao.updateSiteHistory(toEntity(serverItem))
                        } else {
                            // Insert new item
                            siteHistoryDao.insertSiteHistory(toEntity(serverItem))
                        }
                    }

                    // Handle deletions - remove items that exist locally but not on server
                    localHistoryItems.forEach { localItem ->
                        if (localItem.id !in serverItemIds) {
                            Log.d(TAG, "Deleting removed item: $localItem")
                            siteHistoryDao.deleteSiteHistory(localItem)
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to fetch history from server: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing site history", e)
                e.printStackTrace()
            }
        }
    }


    fun toEntity(siteHistoryItemResponse: SiteHistoryItemResponse): SiteHistory {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val date = dateFormat.parse(siteHistoryItemResponse.createdAt)
        val timestamp = date?.time ?: 0L  // Use 0L if date is null

        return SiteHistory(
            id = siteHistoryItemResponse._id,
            siteInfoId = siteHistoryItemResponse.siteInfoId,
            userId = siteHistoryItemResponse.userId,
            geohash = siteHistoryItemResponse.geohash,
            latitude = siteHistoryItemResponse.latitude,
            longitude = siteHistoryItemResponse.longitude,
            createdAt = timestamp  // Store the timestamp as a Long
        )
    }


}

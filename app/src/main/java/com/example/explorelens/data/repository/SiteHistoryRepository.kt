package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.model.siteHistory.CreateSiteHistoryRequest
import com.example.explorelens.data.model.siteHistory.SiteHistoryItemResponse
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID


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
            return Result.failure(IllegalArgumentException("UserEntity ID cannot be null or blank"))
        }

        return try {
            Log.d(TAG, "Resetting site history for user: $userId")

            // First, get count of site history for this user from local database
            val historyCount = siteHistoryDao.getSiteHistoryCountForUser(userId)
            Log.d(TAG, "Found $historyCount site history items to delete")

            if (historyCount == 0) {
                Log.d(TAG, "No site history found for user")
                return Result.success(Unit)
            }

            // Try to delete all site history from server using the new endpoint
            val response = siteHistoryApi.deleteAllSiteHistoryForUser(userId)

            if (response.isSuccessful) {
                Log.d(TAG, "Successfully deleted all site history from server for user: $userId")

                // Only clear local database if server deletion was successful
                siteHistoryDao.clearSiteHistoryForUser(userId)
                Log.d(TAG, "Local site history cleared successfully ($historyCount items)")

                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to delete site history from server. Response code: ${response.code()}, message: ${response.message()}")

                // Don't clear local database if server deletion failed
                Result.failure(Exception("Server deletion failed: ${response.code()} - ${response.message()}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during site history reset", e)

            // Don't clear local database if there was an exception
            Result.failure(e)
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

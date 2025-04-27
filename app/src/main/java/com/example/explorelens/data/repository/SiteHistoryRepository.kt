package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.network.auth.AuthClient
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
    private val siteHistoryApi = AuthClient.siteApi
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

        if (userId != null) {
            Log.d("USERID", userId)
        } else{
            Log.d("USERID", "not found userid")

        }

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
                            Log.d("TAG", "Deleting removed item: $localItem")
                            siteHistoryDao.deleteSiteHistory(localItem)
                        }
                    }
                } else {
                    Log.e("TAG", "Failed to fetch history from server: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("TAG", "Error syncing site history", e)
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

package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.db.siteDetails.SiteDetailsDao
import com.example.explorelens.data.db.siteDetails.SiteDetailsEntity
import com.example.explorelens.data.model.SiteDetails.SiteDetails // API model
import com.example.explorelens.data.model.SiteDetails.SiteDetailsRatingRequest
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SiteDetailsRepository(context: Context) {
    private val TAG = "SiteDetailsRepository"
    private val tokenManager: AuthTokenManager = AuthTokenManager.getInstance(context)
    private val siteDetailsDao: SiteDetailsDao = AppDatabase.getInstance(context).siteDetailsDao()

    suspend fun addRating(siteId: String, rating: Float): Result<SiteDetails> {
        val userId = tokenManager.getUserId()
        val request = userId?.let { SiteDetailsRatingRequest(rating, it) }

        return try {
            val response: Response<SiteDetails>? =
                request?.let { ExploreLensApiClient.siteDetailsApi.addRating(siteId, it) }

            if (response?.isSuccessful == true) {
                response.body()?.let { updatedSiteDetails ->
                    Log.d(TAG, "Rating added successfully: $updatedSiteDetails")

                    // Update local database with new rating
                    val entity = updatedSiteDetails.toEntity()
                    siteDetailsDao.insertSiteDetails(entity)

                    Result.success(updatedSiteDetails)
                } ?: run {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response?.errorBody()?.string()
                val errorMessage = "Error ${response?.code()}: ${errorBody ?: "Unknown error"}"
                Log.e(TAG, errorMessage)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.localizedMessage}", e)
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }

    // Method to sync site details from server to Room
    suspend fun syncSiteDetails(siteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanSiteId = siteId.replace(" ", "")

        return@withContext try {
            Log.d(TAG, "Syncing site details for: $cleanSiteId")

            // Use suspendCancellableCoroutine to convert callback-based API to suspend
            val result = suspendCancellableCoroutine<Result<SiteDetails?>> { continuation ->
                ExploreLensApiClient.siteDetailsApi.getSiteDetails(cleanSiteId)
                    .enqueue(object : Callback<SiteDetails> {
                        override fun onResponse(call: Call<SiteDetails>, response: Response<SiteDetails>) {
                            if (response.isSuccessful) {
                                val siteDetails = response.body()
                                continuation.resume(Result.success(siteDetails))
                            } else {
                                val error = response.errorBody()?.string() ?: "Unknown error"
                                Log.e(TAG, "Sync failed: Error ${response.code()}: $error")
                                continuation.resume(Result.failure(Exception("Error ${response.code()}: $error")))
                            }
                        }

                        override fun onFailure(call: Call<SiteDetails>, t: Throwable) {
                            Log.e(TAG, "Sync network error: ${t.localizedMessage}", t)
                            continuation.resume(Result.failure(Exception("Network error: ${t.localizedMessage}")))
                        }
                    })
            }

            // Process the result
            result.fold(
                onSuccess = { siteDetails ->
                    if (siteDetails != null) {
                        // Save to Room database
                        val entity = siteDetails.toEntity()
                        siteDetailsDao.insertSiteDetails(entity)

                        Log.d(TAG, "Successfully synced site details to Room: ${siteDetails.name}")
                        Result.success(Unit)
                    } else {
                        Log.e(TAG, "Empty response body during sync")
                        Result.failure(Exception("Empty response body"))
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Sync failed: ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.localizedMessage}", e)
            Result.failure(Exception("Sync error: ${e.localizedMessage}"))
        }
    }

    // Method to get site details as LiveData (only from Room)
    fun getSiteDetailsLiveData(siteId: String): LiveData<SiteDetails?> {
        val cleanSiteId = siteId.replace(" ", "")

        return siteDetailsDao.getSiteDetailsById(cleanSiteId).switchMap { cachedEntity: SiteDetailsEntity? ->
            val result = MutableLiveData<SiteDetails?>()
            result.value = cachedEntity?.toModel()
            result
        }
    }

    fun getSiteDetailsNow(siteId: String): SiteDetailsEntity? {
        return siteDetailsDao.getSiteDetailsByIdNow(siteId)
    }

    // Callback-based method that syncs first, then uses LiveData
    fun fetchSiteDetailsWithSync(
        siteId: String,
        onSuccess: (SiteDetails) -> Unit,
        onError: () -> Unit
    ) {
        val cleanSiteId = siteId.replace(" ", "")
        if (cleanSiteId.isBlank()) {
            onError()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // First sync from server to Room
            Log.d(TAG, "Starting sync for site: $cleanSiteId")
            val syncResult = syncSiteDetails(cleanSiteId)

            withContext(Dispatchers.Main) {
                if (syncResult.isSuccess) {
                    Log.d(TAG, "Sync successful, now getting from Room")
                    // Now get from Room
                    GlobalScope.launch(Dispatchers.IO) {
                        val cachedEntity = siteDetailsDao.getSiteDetailsByIdSync(cleanSiteId)

                        withContext(Dispatchers.Main) {
                            if (cachedEntity != null) {
                                onSuccess(cachedEntity.toModel())
                            } else {
                                Log.e(TAG, "No data in Room after successful sync")
                                onError()
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Sync failed, checking for cached data")
                    // If sync fails, try to get from Room anyway (offline support)
                    GlobalScope.launch(Dispatchers.IO) {
                        val cachedEntity = siteDetailsDao.getSiteDetailsByIdSync(cleanSiteId)

                        withContext(Dispatchers.Main) {
                            if (cachedEntity != null) {
                                Log.d(TAG, "Using cached data after sync failure")
                                onSuccess(cachedEntity.toModel())
                            } else {
                                Log.e(TAG, "No cached data available and sync failed")
                                onError()
                            }
                        }
                    }
                }
            }
        }
    }

    // Legacy callback method (kept for backward compatibility)
    fun fetchSiteDetails(
        siteId: String,
        onSuccess: (SiteDetails) -> Unit,
        onError: () -> Unit
    ) {
        // For now, redirect to the new sync method
        fetchSiteDetailsWithSync(siteId, onSuccess, onError)
    }

    // Method to get site details from Room (suspend)
    suspend fun getSiteDetailsFromRoom(siteId: String): SiteDetails? = withContext(Dispatchers.IO) {
        val cleanSiteId = siteId.replace(" ", "")
        return@withContext siteDetailsDao.getSiteDetailsByIdSync(cleanSiteId)?.toModel()
    }

    // Helper to format a site ID
    fun formatSiteId(siteInfoId: String): String {
        return siteInfoId.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }
}

// Extension functions to convert between models
private fun SiteDetails.toEntity(): SiteDetailsEntity {
    return SiteDetailsEntity(
        id = this.id,
        name = this.name,
        description = this.description,
        averageRating = this.averageRating,
        ratingCount = this.ratingCount,
        imageUrl = this.imageUrl
    )
}

private fun SiteDetailsEntity.toModel(): SiteDetails {
    return SiteDetails(
        id = this.id,
        name = this.name,
        description = this.description ?: "", // Handle nullable description
        averageRating = this.averageRating,
        ratingCount = this.ratingCount,
        imageUrl = this.imageUrl
    )
}
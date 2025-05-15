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

    // Method to get site details as LiveData
    fun getSiteDetailsLiveData(siteId: String): LiveData<SiteDetails?> {
        val cleanSiteId = siteId.replace(" ", "")

        return siteDetailsDao.getSiteDetailsById(cleanSiteId).switchMap { cachedEntity: SiteDetailsEntity? ->
            val result = MutableLiveData<SiteDetails?>()

            // Always set an initial value
            result.value = cachedEntity?.toModel()

            // Always try to fetch fresh data from server
            fetchSiteDetailsFromServer(cleanSiteId) { serverSiteDetails ->
                if (serverSiteDetails != null) {
                    result.postValue(serverSiteDetails)
                } else if (cachedEntity == null && result.value == null) {
                    // Only post null if we don't have cached data and no value was set
                    result.postValue(null)
                }
            }

            result
        }
    }

    // Callback-based method for fetching site details
    fun fetchSiteDetails(
        siteId: String,
        onSuccess: (SiteDetails) -> Unit,
        onError: () -> Unit
    ) {
        val cleanSiteId = siteId.replace(" ", "")
        if (cleanSiteId.isBlank()) {
            onError()
            return
        }

        // Check local database first
        GlobalScope.launch(Dispatchers.IO) {
            val cachedEntity = siteDetailsDao.getSiteDetailsByIdSync(cleanSiteId)

            withContext(Dispatchers.Main) {
                if (cachedEntity != null) {
                    onSuccess(cachedEntity.toModel())
                }
            }

            // Always fetch from server for fresh data
            withContext(Dispatchers.Main) {
                fetchSiteDetailsFromServer(cleanSiteId) { serverSiteDetails ->
                    if (serverSiteDetails != null) {
                        onSuccess(serverSiteDetails)
                    } else if (cachedEntity == null) {
                        onError()
                    }
                }
            }
        }
    }

    // Suspend method that actually makes a network call
    suspend fun fetchSiteDetails(siteId: String): Result<SiteDetails> = withContext(Dispatchers.IO) {
        val cleanSiteId = siteId.replace(" ", "")

        try {
            // Check local database first
            val cachedEntity = siteDetailsDao.getSiteDetailsByIdSync(cleanSiteId)

            // Use suspendCancellableCoroutine to convert callback-based API to suspend
            val result = suspendCancellableCoroutine<Result<SiteDetails>> { continuation ->
                fetchSiteDetailsFromServer(cleanSiteId) { siteDetails ->
                    if (siteDetails != null) {
                        continuation.resume(Result.success(siteDetails))
                    } else {
                        cachedEntity?.let {
                            continuation.resume(Result.success(it.toModel()))
                        } ?: continuation.resume(Result.failure(Exception("Failed to fetch site details")))
                    }
                }
            }

            result
        } catch (e: Exception) {
            // Return cached data if available, otherwise error
            val cachedEntity = siteDetailsDao.getSiteDetailsByIdSync(cleanSiteId)
            cachedEntity?.let { Result.success(it.toModel()) }
                ?: Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }

    private fun fetchSiteDetailsFromServer(
        siteId: String,
        callback: (SiteDetails?) -> Unit
    ) {
        ExploreLensApiClient.siteDetailsApi.getSiteDetails(siteId)
            .enqueue(object : Callback<SiteDetails> {
                override fun onResponse(call: Call<SiteDetails>, response: Response<SiteDetails>) {
                    if (response.isSuccessful) {
                        val siteDetails = response.body()
                        if (siteDetails != null) {
                            // Save to local database
                            GlobalScope.launch(Dispatchers.IO) {
                                val entity = siteDetails.toEntity()
                                siteDetailsDao.insertSiteDetails(entity)
                            }
                        }
                        callback(siteDetails)
                    } else {
                        Log.e(TAG, "Failed to fetch site details: ${response.code()}")
                        callback(null)
                    }
                }

                override fun onFailure(call: Call<SiteDetails>, t: Throwable) {
                    Log.e(TAG, "Error fetching site details", t)
                    callback(null)
                }
            })
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
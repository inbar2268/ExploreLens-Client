package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.db.places.Place
import com.example.explorelens.data.db.places.Review
import com.example.explorelens.data.model.PointOfIntrests.PointOfInterest
import com.example.explorelens.data.network.ExploreLensApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class NearbyPlacesRepository(private val context: Context) {

    companion object {
        private const val TAG = "NearbyPlacesRepository"
        private const val CACHE_TIMEOUT_HOURS = 24L

        @Volatile
        private var INSTANCE: NearbyPlacesRepository? = null

        fun getInstance(context: Context): NearbyPlacesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NearbyPlacesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val placeDao = AppDatabase.getInstance(context).placeDao()
    private val nearbyPlacesApi = ExploreLensApiClient.nearbyPlacesApi
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Original method for AR - returns PointOfInterest list
     */
    suspend fun fetchNearbyPlaces(
        lat: Double,
        lng: Double,
        categories: List<String>
    ): Result<List<PointOfInterest>> {
        return try {
            val response = nearbyPlacesApi.getNearbyPlaces(lat, lng, categories)
            if (response.isSuccessful) {
                val places = response.body()
                places?.let {
                    // Cache the places in Room database for later detail viewing
                    cachePlacesInBackground(it)
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }

    /**
     * Get individual place details as LiveData for LayerDetailFragment
     */
    fun getPlaceDetailsLiveData(placeId: String): LiveData<Resource<Place>> {
        return placeDao.getPlace(placeId).map { cachedPlace ->
            when {
                cachedPlace == null -> {
                    // No cached data - this shouldn't happen if places are cached from AR
                    Log.w(TAG, "No cached place found for ID: $placeId")
                    Resource.Error("Place not found in cache")
                }
                isCacheExpired(cachedPlace.lastUpdated) -> {
                    // Cache expired, return cached data but refresh in background
                    triggerPlaceRefresh(placeId, cachedPlace.latitude, cachedPlace.longitude)
                    Resource.Success(cachedPlace, isFromCache = true)
                }
                else -> {
                    // Fresh cached data
                    Resource.Success(cachedPlace, isFromCache = false)
                }
            }
        }
    }

    /**
     * Force refresh place details from server
     */
    suspend fun refreshPlaceDetails(placeId: String): Resource<Place> {
        return try {
            Log.d(TAG, "Force refreshing place details for: $placeId")

            // Get cached place to have coordinates for nearby search
            val cachedPlace = withContext(Dispatchers.IO) {
                placeDao.getPlaceSync(placeId)
            }

            if (cachedPlace != null) {
                // Use cached coordinates to fetch updated nearby places
                val networkResult = fetchAndUpdatePlaceFromNearby(
                    cachedPlace.latitude,
                    cachedPlace.longitude,
                    placeId
                )

                if (networkResult.isSuccess) {
                    val place = networkResult.getOrThrow()
                    placeDao.insertPlace(place)
                    Resource.Success(place, isFromCache = false)
                } else {
                    // Network failed - return cached data
                    Log.w(TAG, "Network refresh failed, using cached data")
                    Resource.Success(cachedPlace, isFromCache = true)
                }
            } else {
                Resource.Error("Cannot refresh place without cached data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during refresh", e)
            Resource.Error(e.message ?: "Refresh failed")
        }
    }

    /**
     * Get cached place details only
     */
    suspend fun getCachedPlaceDetails(placeId: String): Resource<Place> {
        return withContext(Dispatchers.IO) {
            val cachedPlace = placeDao.getPlaceSync(placeId)
            if (cachedPlace != null) {
                Resource.Success(cachedPlace, isFromCache = true)
            } else {
                Resource.Error("No cached data available")
            }
        }
    }

    /**
     * Check if place exists in cache
     */
    suspend fun placeExistsInCache(placeId: String): Boolean {
        return withContext(Dispatchers.IO) {
            placeDao.placeExists(placeId) > 0
        }
    }

    /**
     * Clear expired cache entries
     */
    suspend fun clearExpiredCache() {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(CACHE_TIMEOUT_HOURS * 2)
            placeDao.deleteOldPlaces(cutoffTime)
            Log.d(TAG, "Cleared expired cache entries")
        }
    }

    // Private helper methods

    private fun cachePlacesInBackground(places: List<PointOfInterest>) {
        repositoryScope.launch {
            try {
                places.forEach { pointOfInterest ->
                    val place = mapPointOfInterestToPlace(pointOfInterest)
                    placeDao.insertPlace(place)
                }
                Log.d(TAG, "Cached ${places.size} places in background")
            } catch (e: Exception) {
                Log.e(TAG, "Error caching places", e)
            }
        }
    }

    private suspend fun fetchAndUpdatePlaceFromNearby(
        lat: Double,
        lng: Double,
        targetPlaceId: String
    ): Result<Place> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching updated place from nearby endpoint")

                // Fetch all nearby places and find the target place
                val response = nearbyPlacesApi.getNearbyPlaces(
                    latitude = lat,
                    longitude = lng,
                    categories = listOf("restaurant", "cafe", "bar", "bakery", "lodging", "pharmacy", "gym")
                )

                if (response.isSuccessful) {
                    val places = response.body()
                    val targetPlace = places?.find { it.id == targetPlaceId }

                    if (targetPlace != null) {
                        val place = mapPointOfInterestToPlace(targetPlace)
                        Log.d(TAG, "Successfully found and updated place from nearby results")
                        Result.success(place)
                    } else {
                        Log.e(TAG, "Place not found in nearby results")
                        Result.failure(Exception("Place not found in updated results"))
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

    private fun triggerPlaceRefresh(placeId: String, lat: Double, lng: Double) {
        repositoryScope.launch {
            try {
                val networkResult = fetchAndUpdatePlaceFromNearby(lat, lng, placeId)
                if (networkResult.isSuccess) {
                    val place = networkResult.getOrThrow()
                    placeDao.insertPlace(place)
                    Log.d(TAG, "Background refresh completed for: $placeId")
                } else {
                    Log.w(TAG, "Background refresh failed: ${networkResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background refresh exception", e)
            }
        }
    }

    private fun mapPointOfInterestToPlace(pointOfInterest: PointOfInterest): Place {
        return Place(
            placeId = pointOfInterest.id,
            name = pointOfInterest.name,
            latitude = pointOfInterest.location.lat,
            longitude = pointOfInterest.location.lng,
            rating = pointOfInterest.rating,
            type = pointOfInterest.type,
            editorialSummary = null, // PointOfInterest doesn't have this field
            website = null, // PointOfInterest doesn't have this field
            priceLevel = null, // PointOfInterest doesn't have this field
            elevation = pointOfInterest.elevation,
            address = pointOfInterest.address,
            phoneNumber = pointOfInterest.phoneNumber,
            businessStatus = pointOfInterest.businessStatus,
            openNow = pointOfInterest.openingHours?.openNow,
            weekdayText = pointOfInterest.openingHours?.weekdayText,
            reviews = null, // PointOfInterest doesn't have reviews
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun isCacheExpired(cacheTimestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - cacheTimestamp
        val maxCacheAge = TimeUnit.HOURS.toMillis(CACHE_TIMEOUT_HOURS)
        return cacheAge > maxCacheAge
    }

}
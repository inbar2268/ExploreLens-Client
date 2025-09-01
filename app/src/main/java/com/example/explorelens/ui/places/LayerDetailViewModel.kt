package com.example.explorelens.ui.places

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.explorelens.data.db.places.Place
import com.example.explorelens.data.db.places.Review
import com.example.explorelens.data.repository.NearbyPlacesRepository
import com.example.explorelens.data.repository.Resource
import kotlinx.coroutines.launch

class LayerDetailViewModel(application: Application) : AndroidViewModel(application) {

    sealed class PlaceDetailState {
        object Loading : PlaceDetailState()
        data class Success(
            val place: Place,
            val isFromCache: Boolean = false
        ) : PlaceDetailState()
        data class Error(val message: String) : PlaceDetailState()
    }

    private val nearbyPlacesRepository = NearbyPlacesRepository.getInstance(application)

    private val _placeId = MutableLiveData<String>()
    private val _isRefreshing = MutableLiveData<Boolean>()

    val isRefreshing: LiveData<Boolean> = _isRefreshing

    // Reactive place details based on placeId
// Replace your placeDetailState LiveData with this:

    val placeDetailState: LiveData<PlaceDetailState> = _placeId.switchMap { placeId ->
        val liveData = MutableLiveData<PlaceDetailState>()

        if (placeId.isNullOrBlank()) {
            liveData.value = PlaceDetailState.Error("Invalid place ID")
            return@switchMap liveData
        }

        // Quick fix: Handle mock places first
        if (placeId.startsWith("mock_")) {
            val mockPlace = createMockPlace(placeId)
            if (mockPlace != null) {
                liveData.value = PlaceDetailState.Success(
                    place = mockPlace,
                    isFromCache = false
                )
                return@switchMap liveData
            }
        }

        // For non-mock places, use repository
        nearbyPlacesRepository.getPlaceDetailsLiveData(placeId).switchMap { resource ->
            val resultLiveData = MutableLiveData<PlaceDetailState>()

            when (resource) {
                is Resource.Loading -> {
                    resultLiveData.value = PlaceDetailState.Loading
                }
                is Resource.Success -> {
                    val place = resource.data!!
                    resultLiveData.value = PlaceDetailState.Success(
                        place = place,
                        isFromCache = resource.isFromCache
                    )
                }
                is Resource.Error -> {
                    resultLiveData.value = PlaceDetailState.Error("couldn't load nearby places: network error" ?: "Unknown error")
                }
            }

            resultLiveData
        }
    }

    /**
     * Set the place ID to fetch details for
     */
    fun setPlaceId(placeId: String) {
        Log.d("LayerDetailViewModel", "setPlaceId called with: $placeId")

        if (_placeId.value != placeId) {
            _placeId.value = placeId
            Log.d("LayerDetailViewModel", "PlaceId set to: $placeId")

            // Debug: Test mock place creation
            if (placeId.startsWith("mock_")) {
                val mockPlace = createMockPlace(placeId)
                Log.d("LayerDetailViewModel", "Mock place created: ${mockPlace?.name ?: "null"}")
            }
        } else {
            Log.d("LayerDetailViewModel", "PlaceId unchanged: $placeId")
        }
    }

    /**
     * Force refresh place details
     */
    fun refreshPlaceDetails() {
        val placeId = _placeId.value
        if (placeId.isNullOrBlank()) return

        _isRefreshing.value = true

        viewModelScope.launch {
            try {
                nearbyPlacesRepository.refreshPlaceDetails(placeId)
            } catch (e: Exception) {
                // Error will be handled by the LiveData observer
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Get current place ID
     */
    fun getCurrentPlaceId(): String? = _placeId.value

    /**
     * Check if place exists in cache
     */
    fun checkPlaceInCache(placeId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val exists = nearbyPlacesRepository.placeExistsInCache(placeId)
            callback(exists)
        }
    }

    /**
     * Clear expired cache
     */
    fun clearExpiredCache() {
        viewModelScope.launch {
            nearbyPlacesRepository.clearExpiredCache()
        }
    }
    private fun createMockPlace(placeId: String): Place? {
        return when (placeId) {
            "mock_cafe_123" -> Place(
                placeId = placeId,
                name = "Mock Coffee Corner",
                latitude = 31.928327,
                longitude = 34.784898,
                rating = 4.2f,
                type = "cafe",
                editorialSummary = "A cozy neighborhood coffee shop with excellent espresso and fresh pastries. Perfect for working or catching up with friends.",
                website = "www.mockcoffee.com",
                priceLevel = 2,
                elevation = 15.0,
                address = "1 Mock Road, Hod Hasharon",
                phoneNumber = "050-1112233",
                businessStatus = "OPERATIONAL",
                openNow = true,
                weekdayText = listOf(
                    "Monday: 7:00 AM – 6:00 PM",
                    "Tuesday: 7:00 AM – 6:00 PM",
                    "Wednesday: 7:00 AM – 6:00 PM",
                    "Thursday: 7:00 AM – 6:00 PM",
                    "Friday: 7:00 AM – 6:00 PM",
                    "Saturday: 9:00 AM – 2:00 PM",
                    "Sunday: Closed"
                ),
                reviews = listOf(
                    Review(
                        authorName = "Sarah M.",
                        rating = 5.0f,
                        relativeTimeDescription = "2 weeks ago",
                        text = "Amazing coffee and friendly staff! The atmosphere is perfect for working.",
                        time = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000) // 2 weeks ago
                    ),
                    Review(
                        authorName = "David L.",
                        rating = 4.0f,
                        relativeTimeDescription = "1 month ago",
                        text = "Great espresso and pastries. Can get busy during lunch hours.",
                        time = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000) // 1 month ago
                    )
                ),
                lastUpdated = System.currentTimeMillis()
            )

            "mock_museum_456" -> Place(
                placeId = placeId,
                name = "Virtual History Museum",
                latitude = 31.928319,
                longitude = 34.784890,
                rating = 4.8f,
                type = "museum",
                editorialSummary = "An innovative museum showcasing local history through interactive digital exhibits and virtual reality experiences.",
                website = "www.virtualmuseum.com",
                priceLevel = 1,
                elevation = 15.0,
                address = "789 Pixel Lane, Hod Hasharon",
                phoneNumber = "050-7778888",
                businessStatus = "OPERATIONAL",
                openNow = false,
                weekdayText = listOf(
                    "Monday: 10:00 AM – 5:00 PM",
                    "Tuesday: 10:00 AM – 5:00 PM",
                    "Wednesday: 10:00 AM – 5:00 PM",
                    "Thursday: 10:00 AM – 5:00 PM",
                    "Friday: 10:00 AM – 5:00 PM",
                    "Saturday: 10:00 AM – 5:00 PM",
                    "Sunday: 10:00 AM – 5:00 PM"
                ),
                reviews = listOf(
                    Review(
                        authorName = "Maria K.",
                        rating = 5.0f,
                        relativeTimeDescription = "3 days ago",
                        text = "Fascinating interactive exhibits! The VR experience was incredible.",
                        time = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000) // 3 days ago
                    ),
                    Review(
                        authorName = "Alex R.",
                        rating = 4.5f,
                        relativeTimeDescription = "1 week ago",
                        text = "Really enjoyed the digital displays. Great for families with kids.",
                        time = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // 1 week ago
                    )
                ),
                lastUpdated = System.currentTimeMillis()
            )

            else -> null
        }
    }
}
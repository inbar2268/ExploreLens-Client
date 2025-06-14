package com.example.explorelens.ui.places

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.explorelens.data.db.places.Place
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
    val placeDetailState: LiveData<PlaceDetailState> = _placeId.switchMap { placeId ->
        if (placeId.isNullOrBlank()) {
            val errorLiveData = MutableLiveData<PlaceDetailState>()
            errorLiveData.value = PlaceDetailState.Error("Invalid place ID")
            errorLiveData
        } else {
            nearbyPlacesRepository.getPlaceDetailsLiveData(placeId).switchMap { resource ->
                val liveData = MutableLiveData<PlaceDetailState>()

                when (resource) {
                    is Resource.Loading -> {
                        liveData.value = PlaceDetailState.Loading
                    }
                    is Resource.Success -> {
                        val place = resource.data!!
                        liveData.value = PlaceDetailState.Success(
                            place = place,
                            isFromCache = resource.isFromCache
                        )
                    }
                    is Resource.Error -> {
                        liveData.value = PlaceDetailState.Error(resource.message ?: "Unknown error")
                    }
                }

                liveData
            }
        }
    }

    /**
     * Set the place ID to fetch details for
     */
    fun setPlaceId(placeId: String) {
        if (_placeId.value != placeId) {
            _placeId.value = placeId
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
}
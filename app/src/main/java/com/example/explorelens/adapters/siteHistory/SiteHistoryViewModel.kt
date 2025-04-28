package com.example.explorelens.adapters.siteHistory

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.utils.GeoLocationUtils
import kotlinx.coroutines.launch

class SiteHistoryViewModel(
    private val repository: SiteHistoryRepository,
    private val geoLocationUtils: GeoLocationUtils
) : ViewModel() {

    // Get site history for a user
    fun getSiteHistoryByUserId(userId: String): LiveData<List<SiteHistory>> {
        return repository.getSiteHistoryByUserId(userId)
    }

    // Create site history entry
    fun createSiteHistory(siteInfoId: String, location: Location?) {
        viewModelScope.launch {
            // Use the GeoLocationUtils instance to get the geoHash
            val geoHash = geoLocationUtils.getGeoHash() ?: ""
            val latitude = location?.latitude ?: 0.0
            val longitude = location?.longitude ?: 0.0

            repository.createSiteHistory(siteInfoId, geoHash, latitude, longitude)
        }
    }

    // Sync with server
    fun syncSiteHistory(userId: String) {
        viewModelScope.launch {
            repository.syncSiteHistory(userId)
        }
    }

    // Factory for creating the ViewModel with dependencies
    class Factory(
        private val repository: SiteHistoryRepository,
        private val geoLocationUtils: GeoLocationUtils
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SiteHistoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SiteHistoryViewModel(repository, geoLocationUtils) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

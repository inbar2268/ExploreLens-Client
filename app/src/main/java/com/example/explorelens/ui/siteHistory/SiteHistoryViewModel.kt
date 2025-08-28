package com.example.explorelens.ui.siteHistory

import android.location.Location
import androidx.lifecycle.*
import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.repository.SiteHistoryRepository
import com.example.explorelens.utils.GeoLocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SiteHistoryViewModel(
    private val repository: SiteHistoryRepository,
    private val geoLocationUtils: GeoLocationUtils
) : ViewModel() {

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    fun getSiteHistoryByUserId(userId: String): LiveData<List<SiteHistory>> {
        _loading.value = true

        // Get data from repository
        val repositoryData = repository.getSiteHistoryByUserId(userId)

        // Create a mediator to handle loading state
        val result = MediatorLiveData<List<SiteHistory>>()

        result.addSource(repositoryData) { historyList ->
            _loading.postValue(false)
            result.postValue(historyList)
        }

        return result
    }

    fun createSiteHistory(siteInfoId: String, location: Location?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Use the GeoLocationUtils instance to get the geoHash
                val geoHash = geoLocationUtils.getGeoHash() ?: ""
                val latitude = location?.latitude ?: 0.0
                val longitude = location?.longitude ?: 0.0
                repository.createSiteHistory(siteInfoId, geoHash, latitude, longitude)
            }
        }
    }

    fun syncSiteHistory(userId: String) {
        viewModelScope.launch {
            repository.syncSiteHistory(userId)
        }
    }

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
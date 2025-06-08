package com.example.explorelens.ar.components

import android.location.Location
import android.util.Log
import com.example.explorelens.adapters.siteHistory.SiteHistoryViewModel
import com.example.explorelens.data.model.siteDetectionData.ImageAnalyzedResult
import com.example.explorelens.utils.GeoLocationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SiteHistoryHelper(
    private val siteHistoryViewModel: SiteHistoryViewModel,
    private val geoLocationUtils: GeoLocationUtils,
    private val networkScope: CoroutineScope
) {
    companion object {
        private const val TAG = "SiteHistoryHelper"
    }

    fun createSiteHistoryForDetectedObject(
        result: ImageAnalyzedResult,
        location: Location?
    ) {
        networkScope.launch { // Or
            val currentLocation = location ?: geoLocationUtils.getSingleCurrentLocation()
            currentLocation?.let {
                geoLocationUtils.updateLocation(it)
                val geoHash = geoLocationUtils.getGeoHash() ?: ""
                siteHistoryViewModel.createSiteHistory(
                    siteInfoId = result.siteInfoId,
                    currentLocation
                )
                Log.d(
                    TAG,
                    "Saved site history with geoHash: $geoHash, lat: ${currentLocation.latitude}, long: ${currentLocation.longitude}"
                )
            }
        }
    }
}
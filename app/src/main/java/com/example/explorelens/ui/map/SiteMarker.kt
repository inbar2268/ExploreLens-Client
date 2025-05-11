package com.example.explorelens.ui.map

import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker

/**
 * Class to hold complete site marker data
 */
data class SiteMarker(
    val siteHistory: SiteHistory,
    val latLng: LatLng,
    val siteDetails: SiteDetails? = null,
    var marker: Marker? = null
) {
    val siteId: String
        get() = siteHistory.siteInfoId

    val imageUrl: String?
        get() = siteDetails?.imageUrl

    val name: String
        get() = siteDetails?.name ?: formatSiteId(siteId)

    val visitDate: String
        get() = "Visited: ${formatDate(siteHistory.createdAt)}"

    private fun formatSiteId(siteInfoId: String): String {
        return siteInfoId.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun formatDate(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        return format.format(date)
    }
}
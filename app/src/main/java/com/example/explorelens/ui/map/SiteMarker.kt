// SiteMarker.kt
package com.example.explorelens.ui.map

import com.example.explorelens.data.db.siteHistory.SiteHistory
import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SiteMarker(
    val siteHistory: SiteHistory,
    val latLng: LatLng,
    val siteDetails: SiteDetails? = null,
    var marker: Marker? = null
) {
    val siteId: String
        get() = siteHistory.siteInfoId

    val imageUrl: String?
        get() = siteDetails?.imageUrl?.takeIf { it.isNotEmpty() }

    val name: String
        get() = siteDetails?.name?.takeIf { it.isNotEmpty() } ?: formatSiteId(siteId)

    val visitDate: String
        get() = "Visited: ${formatDate(siteHistory.createdAt)}"

    fun formatSiteId(siteInfoId: String): String {
        return siteInfoId.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return format.format(date)
    }
}
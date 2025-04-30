
package com.example.explorelens.data.db.siteHistory
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_history")
data class SiteHistory(
    @PrimaryKey val id: String,
    val siteInfoId: String,
    val userId: String,
    val geohash: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = System.currentTimeMillis()
)


package com.example.explorelens.data.db.siteDetails

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_details")
data class SiteDetailsEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val averageRating: Float,
    val ratingCount: Int,
    val imageUrl: String?
)

package com.example.explorelens.data.db.places

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.explorelens.data.db.Converters

@Entity(tableName = "places")
@TypeConverters(Converters::class)
data class Place(
    @PrimaryKey
    val placeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Float,
    val type: String,
    val editorialSummary: String? = null,
    val website: String? = null,
    val priceLevel: Int? = null,
    val elevation: Double? = null,
    val address: String? = null,
    val phoneNumber: String? = null,
    val businessStatus: String? = null,
    val openNow: Boolean? = null,
    val weekdayText: List<String>? = null,
    val reviews: List<Review>? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class Review(
    val authorName: String,
    val rating: Float,
    val relativeTimeDescription: String,
    val text: String,
    val time: Long
)
package com.example.explorelens.data.db.statistics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_statistics")
data class UserStatisticsEntity(
    @PrimaryKey val userId: String,
    val percentageVisited: String,
    val countryCount: Int,
    val countries: List<String>,
    val siteCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)
package com.example.explorelens.data.model.statistics

data class UserStatisticsResponse(
    val userId: String,
    val percentageVisited: String,
    val countryCount: Int,
    val siteCount: Int,
    val createdAt: String? = null,
    val countries: List<String>,
    val _id: String? = null
)
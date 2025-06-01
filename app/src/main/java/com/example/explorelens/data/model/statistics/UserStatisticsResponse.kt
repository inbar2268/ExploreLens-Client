package com.example.explorelens.data.model.statistics

data class UserStatisticsResponse(
    val userId: String,
    val percentageVisited: String,
    val countryCount: Int,
    val continents: List<String>,
    val siteCount: Int,
    val createdAt: String? = null,
    val _id: String? = null
)
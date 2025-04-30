package com.example.explorelens.data.model

data class CreateSiteHistoryRequest(val siteInfoId: String, val userId: String, val geohash: String, val latitude: Double, val longitude: Double, val createdAt: String)

data class SiteHistoryItemResponse(val _id: String, val siteInfoId: String, val userId: String, val geohash: String, val latitude: Double, val longitude: Double, val createdAt: String)

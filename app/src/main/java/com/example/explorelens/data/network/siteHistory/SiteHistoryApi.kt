package com.example.explorelens.data.network.siteHistory

import com.example.explorelens.data.model.siteHistory.CreateSiteHistoryRequest
import com.example.explorelens.data.model.siteHistory.SiteHistoryItemResponse
import retrofit2.Response
import retrofit2.http.*

interface SiteHistoryApi {
    @POST("/siteinfo_history")
    suspend fun createSiteHistory(@Body request: CreateSiteHistoryRequest): Response<SiteHistoryItemResponse>

    @GET("/siteinfo_history/{id}")
    suspend fun getSiteHistoryById(@Path("id") id: String): Response<SiteHistoryItemResponse>

    @GET("/siteinfo_history/user/{userId}")
    suspend fun getSitesHistoryByUserId(@Path("userId") userId: String): Response<List<SiteHistoryItemResponse>>

    @DELETE("/siteinfo_history/{id}")
    suspend fun deleteSiteHistoryById(@Path("id") id: String): Response<Unit>

    // New endpoint to delete all site history for a user
    @DELETE("/siteinfo_history/user/{userId}")
    suspend fun deleteAllSiteHistoryForUser(@Path("userId") userId: String): Response<Unit>
}
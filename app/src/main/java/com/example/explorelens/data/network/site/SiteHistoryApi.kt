package com.example.explorelens.data.network.site

import com.example.explorelens.data.model.CreateSiteHistoryRequest
import com.example.explorelens.data.model.SiteHistoryItemResponse
import com.example.explorelens.data.model.ResetHistoryResponse
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
}
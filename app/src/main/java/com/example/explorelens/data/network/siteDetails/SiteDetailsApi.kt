package com.example.explorelens.data.network.siteDetails

import com.example.explorelens.data.model.SiteDetails.SiteDetails
import com.example.explorelens.data.model.SiteDetails.SiteDetailsRatingRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SiteDetailsApi {
    @GET("/site-info/{siteid}")
    suspend fun getSiteDetails(@Path("siteid") siteid: String): Response<SiteDetails>


    @POST("/site-info/rating/{siteId}")
    suspend fun addRating(
        @Path("siteId") siteId: String,
        @Body siteDetailsRatingRequest: SiteDetailsRatingRequest
    ): Response<SiteDetails>

}
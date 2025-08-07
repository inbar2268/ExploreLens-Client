package com.example.explorelens.data.network.siteDetails

import com.example.explorelens.data.model.comments.Review
import com.example.explorelens.data.model.comments.ReviewRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ReviewsApi {
    @GET("/reviews/{siteId}")
    suspend fun getSiteReviews(@Path("siteId") siteId: String): Response<List<Review>>

    @POST("/reviews/{siteId}")
    suspend fun createReview(
        @Path("siteId") siteId: String,
        @Body reviewRequest: ReviewRequest
    ): Response<Review>
}
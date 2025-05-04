package com.example.explorelens.data.network

import com.example.explorelens.data.model.comments.Comment
import com.example.explorelens.data.model.comments.CommentRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CommentsApi {
    @GET("/comments/{siteId}")
    suspend fun getSiteComments(@Path("siteId") siteId: String): Response<List<Comment>>

    @POST("/comments/{siteId}")
    suspend fun createComment(
        @Path("siteId") siteId: String,
        @Body commentRequest: CommentRequest
    ): Response<Comment>
}
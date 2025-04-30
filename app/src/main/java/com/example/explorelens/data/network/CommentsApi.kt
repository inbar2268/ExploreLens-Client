package com.example.explorelens.data.network

import com.example.explorelens.data.model.Comment
import com.example.explorelens.data.model.comments.SiteComments
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface CommentsApi {
    @GET("/comments/{siteId}")
    fun getSiteComments(@Path("siteId") siteId: String): Call<List<Comment>>
}
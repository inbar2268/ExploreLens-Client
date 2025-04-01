package com.example.explorelens.networking

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface SiteDetailsApi {
    @GET("/site-info/site-details")
    fun getSiteDetails(@Query("siteName") siteName: String): Call<String>
}
package com.example.explorelens.data.network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface SiteDetailsApi {
    @GET("/site-info/sitename/{siteName}")
    fun getSiteDetails(@Path("siteName") siteName: String): Call<SiteInfo>
}
package com.example.explorelens.data.network

import com.example.explorelens.data.model.PointOfIntrests.PointOfInterest
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NearbyPlacesApi {
    @GET("/places/nearby/")
    suspend fun getNearbyPlaces(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("categories") categories: List<String>
    ): Response<List<PointOfInterest>>

}



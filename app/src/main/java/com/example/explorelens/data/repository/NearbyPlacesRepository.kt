package com.example.explorelens.data.repository

import android.content.Context
import com.example.explorelens.data.model.PointOfIntrests.PointOfInterest
import com.example.explorelens.data.network.ExploreLensApiClient

class NearbyPlacesRepository() {
    suspend fun fetchNearbyPlaces(
        lat: Double,
        lng: Double,
        categories: List<String>
    ): Result<List<PointOfInterest>> {
        return try {
            val response =
                ExploreLensApiClient.nearbyPlacesApi.getNearbyPlaces(lat, lng, categories)
            if (response.isSuccessful) {
                  response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response body"))
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                 Result.failure(Exception("Error ${response.code()}: $error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }
}
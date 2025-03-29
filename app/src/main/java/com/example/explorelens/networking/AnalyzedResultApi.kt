package com.example.explorelens.model.networking

import com.example.explorelens.networking.allImageAnalyzedResults
import okhttp3.MultipartBody
import retrofit2.http.POST
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.Part


interface AnalyzedResultApi {
    @Multipart
    @POST("/site-info/mock-data")
    fun getAnalyzedResult(
        @Part image: MultipartBody.Part
    ): Call<allImageAnalyzedResults>
}

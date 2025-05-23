package com.example.explorelens.data.network.detectionResult

import com.example.explorelens.data.model.siteDetectionData.ImageAnalyzedResult
import okhttp3.MultipartBody
import retrofit2.http.POST
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.Part


interface AnalyzedResultApi {
    @Multipart
    @POST("/site-info/detect-site")
    suspend fun getAnalyzedResult(
        @Part image: MultipartBody.Part
    ): Response<ImageAnalyzedResult>
}

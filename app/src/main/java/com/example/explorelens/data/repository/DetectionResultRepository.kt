package com.example.explorelens.data.repository

import android.content.Context
import com.example.explorelens.data.model.comments.Review
import com.example.explorelens.data.model.siteDetectionData.ImageAnalyzedResult
import com.example.explorelens.data.network.ExploreLensApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class DetectionResultRepository() {

    suspend fun getAnalyzedResult(path: String): Result<ImageAnalyzedResult> {
        return try {
            val file = File(path)
            val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
            val multipartBody = MultipartBody.Part.createFormData("image", file.name, requestBody)

            val response = ExploreLensApiClient.analyzedResultApi.getAnalyzedResult(multipartBody)

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
package com.example.explorelens.data.network.user

import com.example.explorelens.data.model.user.UploadProfilePictureResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface UploadProfilePictureApi {
    @Multipart
    @POST("file")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part
    ): Response<UploadProfilePictureResponse>
}

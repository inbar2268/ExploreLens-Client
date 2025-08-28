package com.example.explorelens.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.db.user.UserEntity
import com.example.explorelens.data.model.user.UserResponse
import com.example.explorelens.data.model.user.UpdateUserRequest
import com.example.explorelens.data.model.user.UploadProfilePictureResponse
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class UserRepository(private val context: Context) {
    private val TAG = "UserRepository"
    private val userDao = AppDatabase.getInstance(context).userDao()
    private val userApiService = ExploreLensApiClient.userApi
    private val uploadApiService = ExploreLensApiClient.uploadProfilePictureApi
    private val tokenManager = AuthTokenManager.getInstance(context)

    // Allowed image types that match your server
    private val allowedImageTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    )

    suspend fun uploadProfileImage(imageUri: Uri): Result<String> {
        return try {
            // Validate file type first
            val mimeType = getMimeType(imageUri)
            if (mimeType == null || !allowedImageTypes.contains(mimeType)) {
                return Result.failure(Exception("Invalid file type. Only JPEG, PNG, GIF, and WebP images are allowed."))
            }

            val file = createTempFileFromUri(imageUri, mimeType)
            if (file == null) {
                return Result.failure(Exception("Failed to process image file"))
            }

            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            Log.d(TAG, "Uploading image with MIME type: $mimeType, file name: ${file.name}")

            val response: Response<UploadProfilePictureResponse> = uploadApiService.uploadImage(body)

            if (response.isSuccessful) {
                val uploadResponse = response.body()
                if (uploadResponse != null && uploadResponse.url.isNotEmpty()) {
                    file.delete()
                    Log.d(TAG, "Image uploaded successfully: ${uploadResponse.url}")
                    Result.success(uploadResponse.url)
                } else {
                    file.delete()
                    Result.failure(Exception("Invalid response from upload server"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown upload error"
                Log.e(TAG, "Error uploading image: $errorMsg")
                file.delete()
                Result.failure(Exception("Error uploading image: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during image upload", e)
            Result.failure(e)
        }
    }

    private fun getMimeType(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> context.contentResolver.getType(uri)
            "file" -> {
                val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension?.lowercase())
            }
            else -> null
        }
    }

    private fun createTempFileFromUri(uri: Uri, mimeType: String): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return null

            // Determine file extension based on MIME type
            val extension = when (mimeType) {
                "image/jpeg" -> ".jpg"
                "image/png" -> ".png"
                "image/gif" -> ".gif"
                "image/webp" -> ".webp"
                else -> ".jpg" // fallback
            }

            // Create a temporary file with proper extension
            val tempFile = File.createTempFile("temp_image", extension, context.cacheDir)
            val outputStream = FileOutputStream(tempFile)

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Created temp file: ${tempFile.name}, size: ${tempFile.length()} bytes")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error creating temp file from URI", e)
            null
        }
    }

    // Fetch user from API and save to database
    suspend fun fetchAndSaveUser(): Result<UserEntity> {
        val userId = tokenManager.getUserId() ?: return Result.failure(IllegalStateException("UserEntity ID not found"))

        return try {
            val response: Response<UserResponse> = userApiService.getUserById(userId)

            if (response.isSuccessful) {
                val userResponse = response.body()
                if (userResponse != null) {
                    // Convert API response to Room entity
                    val user = UserEntity(
                        id = userResponse._id,
                        username = userResponse.username,
                        email = userResponse.email,
                        profilePictureUrl = userResponse.profilePicture
                    )

                    // Save user to database
                    userDao.saveUser(user)
                    Result.success(user)
                } else {
                    Result.failure(NullPointerException("Response body is null"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Error fetching user: $errorMsg")
                Result.failure(Exception("Error fetching user: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during user fetch", e)
            Result.failure(e)
        }
    }

    // Update user both locally and on server
    suspend fun updateUser(user: UserEntity): Result<UserEntity> {
        return try {
            // First update on server
            val updateRequest = UpdateUserRequest(
                username = user.username,
                email = user.email,
                profilePicture = user.profilePictureUrl
            )

            val response = userApiService.updateUser(user.id, updateRequest)

            if (response.isSuccessful) {
                val userResponse = response.body()
                if (userResponse != null) {
                    // Convert API response to Room entity
                    val updatedUser = UserEntity(
                        id = userResponse._id,
                        username = userResponse.username,
                        email = userResponse.email,
                        profilePictureUrl = userResponse.profilePicture
                    )

                    // Save updated user to local database only after successful server update
                    userDao.saveUser(updatedUser)
                    Result.success(updatedUser)
                } else {
                    Result.failure(NullPointerException("Response body is null"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = if (errorBody != null) {
                    try {
                        val jsonObj = org.json.JSONObject(errorBody)
                        jsonObj.optString("error", "Unknown error")
                    } catch (e: Exception) {
                        // fallback to raw string if parsing fails
                        errorBody
                    }
                } else {
                    "Unknown error"
                }

                Log.e(TAG, "Error updating user: $errorMsg")
                Result.failure(Exception("Error updating user: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during user update - no server connection", e)
            // Return failure without updating locally
            Result.failure(Exception("Update user failed: Network error"))
        }
    }

    // delete user
    suspend fun deleteUser(): Result<Unit>? {
        val userId = tokenManager.getUserId() ?: return null
        if (userId.isNullOrBlank()) {
            Log.e(TAG, "deleteUser called with null or blank userId")
            return Result.failure(IllegalArgumentException("UserEntity ID cannot be null or blank"))
        }

        return try {
            // First, attempt to delete from the server
            val response = userApiService.deleteUser(userId)

            if (response.isSuccessful) {
                Log.d(TAG, "UserEntity deleted from server successfully: $userId")
                userDao.deleteUser(userId)
                Log.d(TAG, "UserEntity deleted from local database successfully: $userId")
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Error deleting user from server: $errorMsg")
                Result.failure(Exception("Error deleting user from server: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during user deletion", e)
            Result.failure(e)
        }
    }

    // Get user from local database
    suspend fun getUserFromDb(): UserEntity? {
        val userId = tokenManager.getUserId() ?: return null
        return userDao.getUserById(userId)
    }

    // Observe user from local database
    fun observeUser(): Flow<UserEntity?> {
        val userId = tokenManager.getUserId() ?: return flowOf(null)
        return userDao.observeUserById(userId)
    }

    suspend fun clearUserData() {
        val userId = tokenManager.getUserId() ?: return
        userDao.deleteUser(userId)
    }

    // Get user by ID from server
    suspend fun getUserById(userId: String): Result<UserEntity> {
        // Safety check for null or blank userId
        if (userId.isNullOrBlank()) {
            Log.e(TAG, "getUserById called with null or blank userId")
            return Result.failure(IllegalArgumentException("UserEntity ID cannot be null or blank"))
        }

        return try {
            val response = userApiService.getUserById(userId)
            if (response.isSuccessful) {
                val userResponse = response.body()
                if (userResponse != null) {
                    val user = UserEntity(
                        id = userResponse._id,
                        username = userResponse.username,
                        email = userResponse.email,
                        profilePictureUrl = userResponse.profilePicture
                    )
                    Result.success(user)
                } else {
                    Result.failure(NullPointerException("Response body is null"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Error fetching user: $errorMsg")
                Result.failure(Exception("Error fetching user: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during getUserById", e)
            Result.failure(e)
        }
    }
}
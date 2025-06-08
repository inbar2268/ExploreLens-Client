package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.db.User
import com.example.explorelens.data.model.UserResponse
import com.example.explorelens.data.model.UpdateUserRequest
import com.example.explorelens.data.network.ExploreLensApiClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import retrofit2.Response

class UserRepository(context: Context) {
    private val TAG = "UserRepository"
    private val userDao = AppDatabase.getInstance(context).userDao()
    private val userApiService = ExploreLensApiClient.userApi
    private val tokenManager = AuthTokenManager.getInstance(context)

    // Fetch user from API and save to database
    suspend fun fetchAndSaveUser(): Result<User> {
        val userId = tokenManager.getUserId() ?: return Result.failure(IllegalStateException("User ID not found"))

        return try {
            val response: Response<UserResponse> = userApiService.getUserById(userId)

            if (response.isSuccessful) {
                val userResponse = response.body()
                if (userResponse != null) {
                    // Convert API response to Room entity
                    val user = User(
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
// Update user both locally and on server
    suspend fun updateUser(user: User): Result<User> {
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
                    val updatedUser = User(
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
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Error updating user: $errorMsg")
                Result.failure(Exception("Error updating user: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during user update - no server connection", e)
            // Return failure without updating locally
            Result.failure(Exception("Cannot update profile without server connection"))
        }
    }

    // delete user
    suspend fun deleteUser(): Result<Unit>? {
        val userId = tokenManager.getUserId() ?: return null
        if (userId.isNullOrBlank()) {
            Log.e(TAG, "deleteUser called with null or blank userId")
            return Result.failure(IllegalArgumentException("User ID cannot be null or blank"))
        }

        return try {
            // First, attempt to delete from the server
            val response = userApiService.deleteUser(userId)

            if (response.isSuccessful) {
                Log.d(TAG, "User deleted from server successfully: $userId")
                userDao.deleteUser(userId)
                Log.d(TAG, "User deleted from local database successfully: $userId")
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
    suspend fun getUserFromDb(): User? {
        val userId = tokenManager.getUserId() ?: return null
        return userDao.getUserById(userId)
    }

    // Observe user from local database
    fun observeUser(): Flow<User?> {
        val userId = tokenManager.getUserId() ?: return flowOf(null)
        return userDao.observeUserById(userId)
    }

    suspend fun clearUserData() {
        val userId = tokenManager.getUserId() ?: return
        userDao.deleteUser(userId)
    }

    // Get user by ID from server
    suspend fun getUserById(userId: String): Result<User> {
        // Safety check for null or blank userId
        if (userId.isNullOrBlank()) {
            Log.e(TAG, "getUserById called with null or blank userId")
            return Result.failure(IllegalArgumentException("User ID cannot be null or blank"))
        }

        return try {
            val response = userApiService.getUserById(userId)
            if (response.isSuccessful) {
                val userResponse = response.body()
                if (userResponse != null) {
                    val user = User(
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
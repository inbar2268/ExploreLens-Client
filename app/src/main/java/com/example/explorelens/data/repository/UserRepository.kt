package com.example.explorelens.data.repository

import android.content.Context
import android.util.Log
import com.example.explorelens.data.db.AppDatabase
import com.example.explorelens.data.db.User
import com.example.explorelens.data.model.UserResponse
import com.example.explorelens.data.network.auth.AuthClient
import com.example.explorelens.data.network.auth.AuthTokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import retrofit2.Response

class UserRepository(context: Context) {
    private val TAG = "UserRepository"
    private val userDao = AppDatabase.getInstance(context).userDao()
    private val userApiService = AuthClient.userApi
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
        userDao.deleteUser()
    }
}
package com.example.explorelens.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.explorelens.data.db.User
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.data.repository.Resource
import com.example.explorelens.data.repository.UserRepository
import com.example.explorelens.data.repository.UserStatisticsRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    // UserState sealed class for handling UI states
    sealed class UserState {
        object Loading : UserState()
        data class Success(val user: User) : UserState()
        data class Error(val message: String) : UserState()
        object Logout : UserState()
    }

    sealed class StatisticsState {
        object Loading : StatisticsState()
        data class Success(
            val percentage: String,
            val countryCount: Int,
            val countries: List<String>,
            val isFromCache: Boolean = false
        ) : StatisticsState()
        data class Error(val message: String) : StatisticsState()
    }

    private val userRepository = UserRepository(application)
    private val userStatisticsRepository = UserStatisticsRepository.getInstance(application)

    private val _userActionState = MutableLiveData<UserState>()
    val userActionState: LiveData<UserState> = _userActionState

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    // Use the reactive LiveData from repository (Single Source of Truth)
    val statisticsState: LiveData<StatisticsState> = userStatisticsRepository
        .getUserStatisticsLiveData()
        .switchMap { resource ->
            val liveData = MutableLiveData<StatisticsState>()

            when (resource) {
                is Resource.Loading -> {
                    liveData.value = StatisticsState.Loading
                }
                is Resource.Success -> {
                    val statistics = resource.data!!
                    liveData.value = StatisticsState.Success(
                        percentage = statistics.percentageVisited,
                        countryCount = statistics.countryCount,
                        countries = statistics.countries ?: emptyList(),
                        isFromCache = resource.isFromCache
                    )
                }
                is Resource.Error -> {
                    liveData.value = StatisticsState.Error(resource.message ?: "Unknown error")
                }
            }

            liveData
        }

    val userState: LiveData<UserState> = userRepository
        .observeUser()
        .map { user ->
            when {
                user != null -> UserState.Success(user)
                else -> {
                    checkUserAuthentication()
                    UserState.Loading
                }
            }
        }
        .catch { exception ->
            emit(UserState.Error(exception.message ?: "Unknown error"))
        }
        .asLiveData(viewModelScope.coroutineContext)



    init {
        initializeUserData()
    }

    private fun initializeUserData() {
        viewModelScope.launch {
            val localUser = userRepository.getUserFromDb()
            if (localUser == null) {
                fetchUserFromServer()
            }
        }
    }

    private suspend fun checkUserAuthentication() {
        val localUser = userRepository.getUserFromDb()
        if (localUser == null) {
            fetchUserFromServer()
        }
    }

    fun fetchUserData() {
        _userActionState.value = UserState.Loading

        viewModelScope.launch {
            val localUser = userRepository.getUserFromDb()

            if (localUser != null) {
                _userActionState.value = UserState.Success(localUser)
            } else {
                fetchUserFromServer()
            }
        }
    }

    private suspend fun fetchUserFromServer() {
        val result = userRepository.fetchAndSaveUser()
        result.fold(
            onSuccess = {
                _userActionState.value = UserState.Success(it)
            },
            onFailure = { exception ->
                _userActionState.value = UserState.Logout
            }
        )
    }

    fun updateUserProfile(updatedUser: User) {
        viewModelScope.launch {
            try {
                userRepository.updateUser(updatedUser)
                // The Flow observer will automatically emit the updated user
            } catch (e: Exception) {
                _userActionState.value = UserState.Error("Failed to update profile: ${e.message}")
            }
        }
    }

    fun syncUserFromServer() {
        viewModelScope.launch {
            try {
                val result = userRepository.fetchAndSaveUser()
                result.fold(
                    onSuccess = {
                        // User will be updated through observer when saved to DB
                    },
                    onFailure = { exception ->
                        _userActionState.value = UserState.Error("Failed to sync user data")
                    }
                )
            } catch (e: Exception) {
                _userActionState.value = UserState.Error("Failed to sync user data")
            }
        }
    }


    fun refreshAllData() {
        _isRefreshing.value = true

        viewModelScope.launch {
            try {
                // Refresh user data
                syncUserFromServer()
                // Refresh statistics (this will automatically update the LiveData observers)
                userStatisticsRepository.refreshStatistics()

            } catch (e: Exception) {
                // Handle any errors during refresh
                _userActionState.value = UserState.Error("Failed to refresh data")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshStatistics() {
        viewModelScope.launch {
            userStatisticsRepository.refreshStatistics()
        }
    }

    fun getCacheInfo() {
        viewModelScope.launch {
            val cacheInfo = userStatisticsRepository.getCacheInfo()
            // You can expose this via LiveData if needed for UI
            android.util.Log.d("ProfileViewModel", "Cache info: $cacheInfo")
        }
    }

    fun clearStatisticsCache() {
        viewModelScope.launch {
            userStatisticsRepository.clearCache()
        }
    }
}
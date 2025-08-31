package com.example.explorelens.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.explorelens.data.db.user.User
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.data.repository.Resource
import com.example.explorelens.data.repository.UserRepository
import com.example.explorelens.data.repository.UserStatisticsRepository
import kotlinx.coroutines.flow.collectLatest
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
    private val authRepository = AuthRepository(application)
    private val userStatisticsRepository = UserStatisticsRepository.getInstance(application)

    private val _userState = MutableLiveData<UserState>()
    val userState: LiveData<UserState> = _userState

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

    init {
        observeUser()
        // Statistics are automatically loaded via LiveData observation
    }

    private fun observeUser() {
        viewModelScope.launch {
            userRepository.observeUser().collectLatest { user ->
                if (user != null) {
                    _userState.value = UserState.Success(user)
                }
            }
        }
    }

    fun fetchUserData() {
        _userState.value = UserState.Loading

        viewModelScope.launch {
            // First, try to get user from local database
            val localUser = userRepository.getUserFromDb()

            if (localUser != null) {
                // If user is found locally, update UI with local data
                _userState.value = UserState.Success(localUser)
            } else {
                // If no user is found locally, fetch user data from server
                val result = userRepository.fetchAndSaveUser()

                result.fold(
                    onSuccess = {
                        // The user data will be updated through the observer once the repository saves it
                    },
                    onFailure = { exception ->
                        // Instead of using Error state, directly use Logout state
                        _userState.value = UserState.Logout
                    }
                )
            }
        }
    }

    fun refreshAllData() {
        _isRefreshing.value = true

        viewModelScope.launch {
            try {
                // Refresh user data
                fetchUserData()

                // Refresh statistics (this will automatically update the LiveData observers)
                userStatisticsRepository.refreshStatistics()

            } catch (e: Exception) {
                // Handle any errors during refresh
                _userState.value = UserState.Error("Failed to refresh data")
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
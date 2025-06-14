package com.example.explorelens.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.explorelens.data.db.User
import com.example.explorelens.data.repository.AuthRepository
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
        data class Success(val percentage: String, val countryCount: Int, val countries: List<String>) : StatisticsState()
        data class Error(val message: String) : StatisticsState()
    }

    private val userRepository = UserRepository(application)
    private val authRepository = AuthRepository(application)
    private val userStatisticsRepository = UserStatisticsRepository(application)

    private val _userState = MutableLiveData<UserState>()
    val userState: LiveData<UserState> = _userState

    private val _statisticsState = MutableLiveData<StatisticsState>()
    val statisticsState: LiveData<StatisticsState> = _statisticsState

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    init {
        observeUser()
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

    fun loadUserStatistics() {
        _statisticsState.value = StatisticsState.Loading

        viewModelScope.launch {
            try {
                val result = userStatisticsRepository.getCurrentUserStatistics()

                result.fold(
                    onSuccess = { statistics ->
                        _statisticsState.value = StatisticsState.Success(
                            percentage = statistics.percentageVisited,
                            countryCount = statistics.countryCount,
                            countries = statistics.countries ?: emptyList()
                        )
                    },
                    onFailure = { error ->
                        val errorMessage = when {
                            error.message?.contains("User ID not found") == true -> "Please log in again"
                            error.message?.contains("network") == true || error.message?.contains("internet") == true -> "Check your internet connection"
                            else -> "Failed to load statistics"
                        }
                        _statisticsState.value = StatisticsState.Error(errorMessage)
                    }
                )
            } catch (e: Exception) {
                _statisticsState.value = StatisticsState.Error("Error loading statistics")
            }
        }
    }

    fun refreshAllData() {
        _isRefreshing.value = true

        viewModelScope.launch {
            fetchUserData()
            loadUserStatistics()
            _isRefreshing.value = false
        }
    }
}
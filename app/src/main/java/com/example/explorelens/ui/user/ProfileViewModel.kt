package com.example.explorelens.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.explorelens.data.db.User
import com.example.explorelens.data.repository.AuthRepository
import com.example.explorelens.data.repository.UserRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(application)
    private val authRepository = AuthRepository(application)

    private val _userState = MutableLiveData<UserState>()
    val userState: LiveData<UserState> = _userState

    init {
        viewModelScope.launch {
            userRepository.observeUser().collectLatest { user ->
                if (user != null) {
                    _userState.value = UserState.Success(user)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.clearUserData()
            authRepository.deleteTokens()
            _userState.postValue(UserState.Logout)
        }
    }

    // Fetch user data from server
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
                        _userState.value = UserState.Error(exception.message ?: "Unknown error")
                    }
                )
            }
        }
    }
}

// State class for the UI
sealed class UserState {
    object Loading : UserState()
    data class Success(val user: User) : UserState()
    data class Error(val message: String) : UserState()
    object Logout : UserState()
}

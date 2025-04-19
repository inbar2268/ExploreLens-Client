package com.example.explorelens.ui.user



import android.app.Application

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.LiveData

import androidx.lifecycle.MutableLiveData

import androidx.lifecycle.viewModelScope

import com.example.explorelens.data.db.User

import com.example.explorelens.data.repository.UserRepository

import kotlinx.coroutines.flow.collectLatest

import kotlinx.coroutines.launch



class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(application)



    private val _userState = MutableLiveData<UserState>()

    val userState: LiveData<UserState> = _userState



    init {

// Start observing user from database

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

            _userState.postValue(UserState.Logout)

        }

    }



// Fetch user data from server

    fun fetchUserData() {

        _userState.value = UserState.Loading



        viewModelScope.launch {

            val result = userRepository.fetchAndSaveUser()



            result.fold(

                onSuccess = { /* User will be updated through the observer */ },

                onFailure = { exception ->

                    _userState.value = UserState.Error(exception.message ?: "Unknown error")



// Try to get user from database as fallback

                    val localUser = userRepository.getUserFromDb()

                    if (localUser != null) {

                        _userState.value = UserState.Success(localUser)

                    }

                }

            )

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
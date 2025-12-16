package com.example.nureform.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.repository.AuthRepository
import com.example.nureform.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun checkUserLoggedIn(): Boolean {
        return authRepository.getCurrentUser() != null
    }

    fun login(email: String, password: String) {
        if (!validateInput(email, password)){
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch {
            val result = authRepository.login(email, password)
            _authState.value = if (result.isSuccess) {
                val user = result.getOrNull()!!
                AuthState.Success(user, "${Constants.SUCCESS_LOGIN_PREFIX}${user.name}!")
            } else {
                AuthState.Error("Authentication failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        when {
            email.isEmpty() -> {
                _authState.value = AuthState.Error(Constants.ERROR_EMAIL_REQUIRED)
                return false
            }
            password.isEmpty() -> {
                _authState.value = AuthState.Error(Constants.ERROR_PASSWORD_REQUIRED)
                return false
            }
            password.length < Constants.MIN_PASSWORD_LENGTH -> {
                _authState.value = AuthState.Error(Constants.ERROR_PASSWORD_LENGTH)
                return false
            }
        }
        return true
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}


package com.example.nureform.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.R
import com.example.nureform.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

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
                val message = getApplication<Application>().getString(R.string.success_login, user.name)
                AuthState.Success(user, message)
            } else {
                AuthState.Error("Authentication failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        val context = getApplication<Application>()
        when {
            email.isEmpty() -> {
                _authState.value = AuthState.Error(context.getString(R.string.error_email_required))
                return false
            }
            password.isEmpty() -> {
                _authState.value = AuthState.Error(context.getString(R.string.error_password_required))
                return false
            }
            password.length < 6 -> {
                _authState.value = AuthState.Error(context.getString(R.string.error_password_length))
                return false
            }
        }
        return true
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}


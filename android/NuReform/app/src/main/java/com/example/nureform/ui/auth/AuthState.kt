package com.example.nureform.ui.auth

import com.example.nureform.data.model.User

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User, val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}


package com.example.nureform.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.model.User
import com.example.nureform.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun loadUserData() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser == null) {
            _user.value = null
            return
        }

        _loading.value = true
        viewModelScope.launch {
            try {
                val userData = authRepository.fetchUserData(currentUser.uid)
                _user.value = userData
            } catch (_: Exception) {
                _user.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}


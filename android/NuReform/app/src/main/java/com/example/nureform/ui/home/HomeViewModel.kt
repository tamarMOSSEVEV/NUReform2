package com.example.nureform.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.model.User
import com.example.nureform.data.repository.AuthRepository
import com.example.nureform.data.repository.ShiftsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val shiftsRepository: ShiftsRepository
) : ViewModel() {

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

    suspend fun canSubmitShifts(nurseId: String): ShiftSubmissionStatus {
        // Check if shifts are already finalized by manager
        val finalizedResult = shiftsRepository.areShiftsFinalized()
        if (finalizedResult.isSuccess && finalizedResult.getOrNull() == true) {
            return ShiftSubmissionStatus.ShiftsFinalized
        }

        // Check if submission window is open
        if (!shiftsRepository.isSubmissionWindowOpen()) {
            return ShiftSubmissionStatus.WindowClosed
        }

        // If window is open and shifts not finalized, allow submission/editing
        return ShiftSubmissionStatus.CanSubmit
    }

    fun logout() {
        authRepository.logout()
    }
}

sealed class ShiftSubmissionStatus {
    object CanSubmit : ShiftSubmissionStatus()
    object WindowClosed : ShiftSubmissionStatus()
    object AlreadySubmitted : ShiftSubmissionStatus()
    object ShiftsFinalized : ShiftSubmissionStatus()
}


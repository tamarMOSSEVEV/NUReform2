package com.example.nureform.ui.nurses

import com.example.nureform.data.model.Nurse

sealed class AddNurseState {
    object Idle : AddNurseState()
    object Loading : AddNurseState()
    data class Success(val nurse: Nurse, val message: String) : AddNurseState()
    data class Error(val message: String, val field: String? = null) : AddNurseState()
}


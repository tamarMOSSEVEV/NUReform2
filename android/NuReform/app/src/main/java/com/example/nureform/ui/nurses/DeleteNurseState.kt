package com.example.nureform.ui.nurses

import com.example.nureform.data.model.Nurse

sealed class DeleteNurseState {

    data class Error(val message: String, val field: String? = null) : DeleteNurseState()
    data class DeleteSuccess(val message: String) : DeleteNurseState()
    object NurseNotFound : DeleteNurseState()
    data class NurseFound(val nurse: Nurse) : DeleteNurseState()
    object Loading : DeleteNurseState()
    object Idle : DeleteNurseState()
}





package com.example.nureform.ui.nursesrequests

import com.example.nureform.data.model.NurseShiftRequest

sealed class NursesRequestsState {
    object Loading : NursesRequestsState()
    data class Success(val requests: List<NurseShiftRequest>, val weekInfo: String) : NursesRequestsState()
    data class Error(val message: String) : NursesRequestsState()
}


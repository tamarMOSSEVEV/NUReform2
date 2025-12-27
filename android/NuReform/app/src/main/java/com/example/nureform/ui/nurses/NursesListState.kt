package com.example.nureform.ui.nurses

import com.example.nureform.data.model.Nurse

sealed class NursesListState {
    data class Error(val message: String) : NursesListState()
    object Empty : NursesListState()
    data class Success(val nurses: List<Nurse>) : NursesListState()
    object Loading : NursesListState()
    object Idle : NursesListState()

}




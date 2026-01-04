package com.example.nureform.ui.shifts
sealed class ChooseShiftsState {
    object Idle : ChooseShiftsState()
    object Loading : ChooseShiftsState()
    data class Success(val message: String) : ChooseShiftsState()
    data class Error(val message: String) : ChooseShiftsState()
    data class AlreadySubmitted(val message: String) : ChooseShiftsState()
    data class WindowClosed(val message: String) : ChooseShiftsState()
}

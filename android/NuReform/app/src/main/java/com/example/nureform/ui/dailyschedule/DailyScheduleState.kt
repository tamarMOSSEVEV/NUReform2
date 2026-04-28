package com.example.nureform.ui.dailyschedule

import com.example.nureform.data.model.NurseSchedule

data class DaySummary(
    val dayName: String,
    val date: String,        // "dd/MM"
    val morning: List<String>,
    val noon: List<String>,
    val evening: List<String>
)

sealed class DailyScheduleState {
    object Idle : DailyScheduleState()
    object Loading : DailyScheduleState()
    data class Success(val days: List<DaySummary>) : DailyScheduleState()
    object Empty : DailyScheduleState()
    data class Error(val message: String) : DailyScheduleState()
}

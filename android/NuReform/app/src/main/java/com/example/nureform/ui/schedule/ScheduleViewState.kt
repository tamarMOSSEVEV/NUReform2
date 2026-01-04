package com.example.nureform.ui.schedule

import com.example.nureform.data.model.NurseSchedule

sealed class ScheduleViewState {
    object Idle : ScheduleViewState()
    object Loading : ScheduleViewState()
    data class Success(val schedules: List<NurseSchedule>) : ScheduleViewState()
    object Empty : ScheduleViewState()
    data class Error(val message: String) : ScheduleViewState()
}


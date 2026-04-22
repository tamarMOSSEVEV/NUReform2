package com.example.nureform.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.model.NurseSchedule
import com.example.nureform.data.repository.ShiftsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeekScheduleData(
    val weekLabel: String,
    val weekDocId: String,
    val status: String?,          // null, "running", "success", "error"
    val schedules: List<NurseSchedule>
)

class ScheduleViewModel(
    private val shiftsRepository: ShiftsRepository
) : ViewModel() {

    private val _currentWeekState = MutableStateFlow<ScheduleViewState>(ScheduleViewState.Idle)
    val currentWeekState: StateFlow<ScheduleViewState> = _currentWeekState.asStateFlow()

    private val _nextWeekState = MutableStateFlow<ScheduleViewState>(ScheduleViewState.Idle)
    val nextWeekState: StateFlow<ScheduleViewState> = _nextWeekState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _currentWeekLabel = MutableStateFlow("")
    val currentWeekLabel: StateFlow<String> = _currentWeekLabel.asStateFlow()

    private val _nextWeekLabel = MutableStateFlow("")
    val nextWeekLabel: StateFlow<String> = _nextWeekLabel.asStateFlow()

    private var isAllNurses = true
    private var nurseName: String? = null

    fun loadSchedule(isAllNurses: Boolean, currentNurseName: String? = null) {
        this.isAllNurses = isAllNurses
        this.nurseName = currentNurseName
        _title.value = if (isAllNurses) "השיבוצים של כל האחיות" else "השיבוץ שלי"

        val currentWeekNum = shiftsRepository.getCurrentWeekNumber()
        val currentYear = shiftsRepository.getCurrentYear()
        val currentDocId = "${currentYear}_${currentWeekNum}"
        _currentWeekLabel.value = "שבוע $currentWeekNum, $currentYear"

        val nextWeekNum = shiftsRepository.getNextWeekNumber()
        val nextYear = shiftsRepository.getNextWeekYear()
        val nextDocId = "${nextYear}_${nextWeekNum}"
        _nextWeekLabel.value = "שבוע $nextWeekNum, $nextYear"

        loadWeek(currentDocId, _currentWeekState)
        loadWeek(nextDocId, _nextWeekState)
    }

    private fun loadWeek(weekDocId: String, stateFlow: MutableStateFlow<ScheduleViewState>) {
        stateFlow.value = ScheduleViewState.Loading

        viewModelScope.launch {
            try {
                // Check status first
                val status = shiftsRepository.getWeekStatus(weekDocId)

                when (status) {
                    "running" -> {
                        stateFlow.value = ScheduleViewState.Running
                        return@launch
                    }
                    "error" -> {
                        stateFlow.value = ScheduleViewState.Error("שגיאה בשיבוץ")
                        return@launch
                    }
                    "success" -> {
                        val result = shiftsRepository.getScheduleForWeek(weekDocId)
                        if (result.isFailure) {
                            stateFlow.value = ScheduleViewState.Error(
                                result.exceptionOrNull()?.message ?: "שגיאה בטעינת נתונים"
                            )
                            return@launch
                        }

                        val schedules = result.getOrDefault(emptyList())
                        val filtered = if (isAllNurses) {
                            schedules
                        } else {
                            schedules.filter { it.nurseName == nurseName }
                        }

                        if (filtered.isEmpty()) {
                            stateFlow.value = ScheduleViewState.Empty
                        } else {
                            stateFlow.value = ScheduleViewState.Success(filtered)
                        }
                    }
                    else -> {
                        // No doc exists
                        stateFlow.value = ScheduleViewState.Empty
                    }
                }
            } catch (e: Exception) {
                stateFlow.value = ScheduleViewState.Error(e.message ?: "שגיאה לא צפויה")
            }
        }
    }
}

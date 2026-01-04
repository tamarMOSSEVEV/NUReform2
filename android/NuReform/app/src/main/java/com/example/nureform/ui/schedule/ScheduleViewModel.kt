package com.example.nureform.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.model.NurseSchedule
import com.example.nureform.data.repository.ShiftsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduleViewModel(
    private val shiftsRepository: ShiftsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ScheduleViewState>(ScheduleViewState.Idle)
    val state: StateFlow<ScheduleViewState> = _state.asStateFlow()

    private val _weekInfo = MutableStateFlow("")
    val weekInfo: StateFlow<String> = _weekInfo.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    fun loadSchedule(isAllNurses: Boolean, currentNurseName: String? = null) {
        val weekNumber = shiftsRepository.getCurrentWeekNumber()
        val year = shiftsRepository.getCurrentYear()
        _weekInfo.value = "שבוע $weekNumber, $year"
        _title.value = if (isAllNurses) "השיבוצים של כל האחיות" else "השיבוץ שלי"

        _state.value = ScheduleViewState.Loading

        viewModelScope.launch {
            try {
                // TODO: Replace with real data from repository
                val mockSchedules = getMockSchedules(isAllNurses, currentNurseName)

                if (mockSchedules.isEmpty()) {
                    _state.value = ScheduleViewState.Empty
                } else {
                    _state.value = ScheduleViewState.Success(mockSchedules)
                }
            } catch (e: Exception) {
                _state.value = ScheduleViewState.Error(e.message ?: "שגיאה בטעינת נתונים")
            }
        }
    }

    private fun getMockSchedules(isAllNurses: Boolean, currentNurseName: String?): List<NurseSchedule> {
        return if (isAllNurses) {
            // Mock data for all nurses
            listOf(
                NurseSchedule(
                    nurseName = "שרה כהן",
                    sunday = "בוקר",
                    monday = "צהריים",
                    tuesday = "X",
                    wednesday = "בוקר, ערב",
                    thursday = "X",
                    friday = "בוקר",
                    saturday = "X"
                ),
                NurseSchedule(
                    nurseName = "רחל לוי",
                    sunday = "צהריים",
                    monday = "ערב",
                    tuesday = "בוקר",
                    wednesday = "X",
                    thursday = "צהריים",
                    friday = "X",
                    saturday = "ערב"
                ),
                NurseSchedule(
                    nurseName = "מרים גולן",
                    sunday = "X",
                    monday = "בוקר",
                    tuesday = "צהריים, ערב",
                    wednesday = "בוקר",
                    thursday = "X",
                    friday = "צהריים",
                    saturday = "בוקר"
                )
            )
        } else {
            // Mock data for current nurse only
            listOf(
                NurseSchedule(
                    nurseName = currentNurseName ?: "אחות נוכחית",
                    sunday = "בוקר",
                    monday = "צהריים",
                    tuesday = "X",
                    wednesday = "בוקר, ערב",
                    thursday = "X",
                    friday = "בוקר",
                    saturday = "X"
                )
            )
        }
    }
}


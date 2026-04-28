package com.example.nureform.ui.dailyschedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.repository.ShiftsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class DailyScheduleViewModel(
    private val shiftsRepository: ShiftsRepository
) : ViewModel() {

    private val _currentWeekState = MutableStateFlow<DailyScheduleState>(DailyScheduleState.Idle)
    val currentWeekState: StateFlow<DailyScheduleState> = _currentWeekState.asStateFlow()

    private val _nextWeekState = MutableStateFlow<DailyScheduleState>(DailyScheduleState.Idle)
    val nextWeekState: StateFlow<DailyScheduleState> = _nextWeekState.asStateFlow()

    private val _currentWeekLabel = MutableStateFlow("")
    val currentWeekLabel: StateFlow<String> = _currentWeekLabel.asStateFlow()

    private val _nextWeekLabel = MutableStateFlow("")
    val nextWeekLabel: StateFlow<String> = _nextWeekLabel.asStateFlow()

    var currentWeekDocId: String = ""
        private set
    var nextWeekDocId: String = ""
        private set

    private val dayFields = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    private val dayNames = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")

    fun loadSummaries() {
        val currentWeekNum = shiftsRepository.getCurrentWeekNumber()
        val currentYear = shiftsRepository.getCurrentYear()
        currentWeekDocId = "${currentYear}_${currentWeekNum}"
        _currentWeekLabel.value = "שבוע $currentWeekNum, $currentYear"

        val nextWeekNum = shiftsRepository.getNextWeekNumber()
        val nextYear = shiftsRepository.getNextWeekYear()
        nextWeekDocId = "${nextYear}_${nextWeekNum}"
        _nextWeekLabel.value = "שבוע $nextWeekNum, $nextYear"

        loadWeek(currentWeekDocId, _currentWeekState)
        loadWeek(nextWeekDocId, _nextWeekState)
    }

    private fun loadWeek(weekDocId: String, stateFlow: MutableStateFlow<DailyScheduleState>) {
        stateFlow.value = DailyScheduleState.Loading
        viewModelScope.launch {
            try {
                val status = shiftsRepository.getWeekStatus(weekDocId)
                if (status != "success") {
                    stateFlow.value = DailyScheduleState.Empty
                    return@launch
                }

                val result = shiftsRepository.getScheduleForWeek(weekDocId)
                if (result.isFailure) {
                    stateFlow.value = DailyScheduleState.Error(
                        result.exceptionOrNull()?.message ?: "שגיאה בטעינת נתונים"
                    )
                    return@launch
                }

                val schedules = result.getOrDefault(emptyList())
                if (schedules.isEmpty()) {
                    stateFlow.value = DailyScheduleState.Empty
                    return@launch
                }

                val dates = weekDates(weekDocId)
                val summaries = dayFields.mapIndexed { index, field ->
                    val morning = mutableListOf<String>()
                    val noon = mutableListOf<String>()
                    val evening = mutableListOf<String>()

                    schedules.forEach { nurse ->
                        val shift = when (field) {
                            "sunday" -> nurse.sunday
                            "monday" -> nurse.monday
                            "tuesday" -> nurse.tuesday
                            "wednesday" -> nurse.wednesday
                            "thursday" -> nurse.thursday
                            "friday" -> nurse.friday
                            "saturday" -> nurse.saturday
                            else -> null
                        }
                        val label = "${nurse.nurseName} (${nurse.jobPercentage}%)"
                        when (shift) {
                            "בוקר" -> morning.add(label)
                            "צהריים" -> noon.add(label)
                            "ערב" -> evening.add(label)
                        }
                    }

                    DaySummary(
                        dayName = dayNames[index],
                        date = dates.getOrElse(index) { "" },
                        morning = morning,
                        noon = noon,
                        evening = evening
                    )
                }

                stateFlow.value = DailyScheduleState.Success(summaries)
            } catch (e: Exception) {
                stateFlow.value = DailyScheduleState.Error(e.message ?: "שגיאה לא צפויה")
            }
        }
    }

    private fun weekDates(weekDocId: String): List<String> {
        return try {
            val parts = weekDocId.split("_")
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.WEEK_OF_YEAR, week)
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            }
            (0..6).map {
                val d = cal.get(Calendar.DAY_OF_MONTH)
                val m = cal.get(Calendar.MONTH) + 1
                val date = "%02d/%02d".format(d, m)
                cal.add(Calendar.DAY_OF_MONTH, 1)
                date
            }
        } catch (e: Exception) {
            List(7) { "" }
        }
    }
}

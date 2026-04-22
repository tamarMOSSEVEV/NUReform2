package com.example.nureform.ui.scheduling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.repository.ScheduleStatus
import com.example.nureform.data.repository.SchedulingRepository
import com.example.nureform.data.repository.ShiftsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RunSchedulingViewModel(
    private val schedulingRepository: SchedulingRepository,
    private val shiftsRepository: ShiftsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<RunSchedulingState>(RunSchedulingState.Loading)
    val state: StateFlow<RunSchedulingState> = _state.asStateFlow()

    private val _weekInfo = MutableStateFlow("")
    val weekInfo: StateFlow<String> = _weekInfo.asStateFlow()

    private var listenerJob: Job? = null

    fun loadNursesRequests() {
        viewModelScope.launch {
            _state.value = RunSchedulingState.Loading
            try {
                val result = shiftsRepository.getAllNursesShiftRequests()
                if (result.isSuccess) {
                    val requests = result.getOrNull() ?: emptyList()
                    val weekNumber = shiftsRepository.getNextWeekNumber()
                    val year = shiftsRepository.getNextWeekYear()
                    _weekInfo.value = "שבוע $weekNumber, $year"

                    if (requests.isEmpty()) {
                        _state.value = RunSchedulingState.Empty
                    } else {
                        // Check if scheduling is already running or done
                        val finalizedResult = shiftsRepository.areShiftsFinalized()
                        if (finalizedResult.isSuccess && finalizedResult.getOrDefault(false)) {
                            _state.value = RunSchedulingState.SchedulingSuccess(
                                message = "השיבוץ כבר בוצע לשבוע זה",
                                week = "${year}_${weekNumber}"
                            )
                        } else {
                            _state.value = RunSchedulingState.Success(requests)
                        }
                    }
                } else {
                    _state.value = RunSchedulingState.Error(
                        result.exceptionOrNull()?.message ?: "שגיאה בטעינת בקשות"
                    )
                }
            } catch (e: Exception) {
                _state.value = RunSchedulingState.Error(e.message ?: "שגיאה בטעינת בקשות")
            }
        }
    }

    fun runScheduling() {
        viewModelScope.launch {
            _state.value = RunSchedulingState.RunningScheduling
            try {
                // Fire HTTP request (returns immediately with "running")
                val result = schedulingRepository.runScheduling()
                if (result.isFailure) {
                    _state.value = RunSchedulingState.SchedulingError(
                        result.exceptionOrNull()?.message ?: "שגיאה בהרצת שיבוץ"
                    )
                    return@launch
                }

                // Start listening to Firestore for real-time status updates
                startListeningToStatus()
            } catch (e: Exception) {
                _state.value = RunSchedulingState.SchedulingError(
                    e.message ?: "שגיאה בהרצת שיבוץ"
                )
            }
        }
    }

    private fun startListeningToStatus() {
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            schedulingRepository.listenToScheduleStatus().collect { status ->
                when (status) {
                    is ScheduleStatus.Running -> {
                        _state.value = RunSchedulingState.RunningScheduling
                    }
                    is ScheduleStatus.Success -> {
                        val weekNumber = shiftsRepository.getNextWeekNumber()
                        val year = shiftsRepository.getNextWeekYear()
                        _state.value = RunSchedulingState.SchedulingSuccess(
                            message = "שיבוץ הושלם בהצלחה!\nשבוע: ${year}_${weekNumber}",
                            week = "${year}_${weekNumber}"
                        )
                        listenerJob?.cancel()
                    }
                    is ScheduleStatus.Error -> {
                        _state.value = RunSchedulingState.SchedulingError(status.message)
                        listenerJob?.cancel()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }
}

sealed class RunSchedulingState {
    object Loading : RunSchedulingState()
    object Empty : RunSchedulingState()
    data class Success(val requests: List<com.example.nureform.data.model.NurseShiftRequest>) : RunSchedulingState()
    data class Error(val message: String) : RunSchedulingState()
    object RunningScheduling : RunSchedulingState()
    data class SchedulingSuccess(val message: String, val week: String) : RunSchedulingState()
    data class SchedulingError(val message: String) : RunSchedulingState()
}

package com.example.nureform.ui.shifts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.repository.ShiftsRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class ChooseShiftsViewModel(
    private val shiftsRepository: ShiftsRepository
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val _state = MutableStateFlow<ChooseShiftsState>(ChooseShiftsState.Idle)
    val state: StateFlow<ChooseShiftsState> = _state.asStateFlow()
    private val _weekInfo = MutableStateFlow("")
    val weekInfo: StateFlow<String> = _weekInfo.asStateFlow()

    private val _existingShifts = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val existingShifts: StateFlow<Map<String, List<String>>> = _existingShifts.asStateFlow()

    private var listenerJob: Job? = null

    fun initialize() {
        val weekNumber = shiftsRepository.getNextWeekNumber()
        val year = shiftsRepository.getNextWeekYear()
        _weekInfo.value = "שבוע $weekNumber, $year"
        startListeningToStatus()
    }

    private fun startListeningToStatus() {
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            _state.value = ChooseShiftsState.Loading
            shiftsRepository.listenToScheduleStatus().collect { status ->
                when (status) {
                    null -> {
                        // No schedule doc — nurse can submit preferences
                        loadExistingPreferences()
                    }
                    "running" -> {
                        _state.value = ChooseShiftsState.SchedulingInProgress("השיבוץ מתבצע כעת...")
                    }
                    "success" -> {
                        _state.value = ChooseShiftsState.WindowClosed("השיבוץ לשבוע זה כבר בוצע. לא ניתן לשנות משמרות")
                    }
                    "error" -> {
                        // Scheduling failed — nurse can still submit
                        loadExistingPreferences()
                    }
                }
            }
        }
    }

    private suspend fun loadExistingPreferences() {
        val nurseId = auth.currentUser?.uid ?: return
        try {
            val result = shiftsRepository.getShiftsForNextWeek(nurseId)
            if (result.isSuccess) {
                val shiftSelection = result.getOrNull()
                if (shiftSelection != null) {
                    _existingShifts.value = shiftSelection.shifts
                }
            }
            _state.value = ChooseShiftsState.Idle
        } catch (e: Exception) {
            _state.value = ChooseShiftsState.Idle
        }
    }

    fun submitShifts(shifts: Map<String, List<String>>) {
        val nurseId = auth.currentUser?.uid
        if (nurseId == null) {
            _state.value = ChooseShiftsState.Error("משתמש לא מחובר")
            return
        }
        val totalShifts = shifts.values.sumOf { it.size }
        if (totalShifts < 10) {
            _state.value = ChooseShiftsState.Error("יש לבחור לפחות 10 משמרות. בחרת $totalShifts")
            return
        }

        val isUpdate = _existingShifts.value.isNotEmpty()
        _state.value = ChooseShiftsState.Loading
        viewModelScope.launch {
            try {
                val result = shiftsRepository.submitWeeklyShifts(nurseId, shifts)
                if (result.isSuccess) {
                    val message = if (isUpdate) {
                        "המשמרות עודכנו בהצלחה!"
                    } else {
                        "המשמרות נשמרו בהצלחה!"
                    }
                    _state.value = ChooseShiftsState.Success(message)
                } else {
                    _state.value = ChooseShiftsState.Error(
                        result.exceptionOrNull()?.message ?: "שגיאה בשמירת המשמרות"
                    )
                }
            } catch (e: Exception) {
                _state.value = ChooseShiftsState.Error(e.message ?: "שגיאה לא צפויה")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }
}

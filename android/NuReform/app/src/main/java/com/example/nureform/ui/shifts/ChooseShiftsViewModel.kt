package com.example.nureform.ui.shifts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.repository.ShiftsRepository
import com.google.firebase.auth.FirebaseAuth
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
    fun initialize() {
        val weekNumber = shiftsRepository.getCurrentWeekNumber()
        val year = shiftsRepository.getCurrentYear()
        _weekInfo.value = "שבוע $weekNumber, $year"
        checkSubmissionStatus()
    }
    private fun checkSubmissionStatus() {
        val nurseId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            if (!shiftsRepository.isSubmissionWindowOpen()) {
                _state.value = ChooseShiftsState.WindowClosed(
                    "חלון הבחירה סגור. ניתן לבחור משמרות רק בין יום ראשון 00:00 ליום שלישי 00:00"
                )
                return@launch
            }
            val result = shiftsRepository.hasSubmittedForCurrentWeek(nurseId)
            if (result.isSuccess && result.getOrNull() == true) {
                _state.value = ChooseShiftsState.AlreadySubmitted(
                    "כבר בחרת משמרות לשבוע זה"
                )
            }
        }
    }
    fun submitShifts(shifts: Map<String, List<String>>) {
        val nurseId = auth.currentUser?.uid
        if (nurseId == null) {
            _state.value = ChooseShiftsState.Error("משתמש לא מחובר")
            return
        }
        val hasAnySelection = shifts.values.any { it.isNotEmpty() }
        if (!hasAnySelection) {
            _state.value = ChooseShiftsState.Error("יש לבחור לפחות משמרת אחת")
            return
        }
        _state.value = ChooseShiftsState.Loading
        viewModelScope.launch {
            try {
                val result = shiftsRepository.submitWeeklyShifts(nurseId, shifts)
                if (result.isSuccess) {
                    _state.value = ChooseShiftsState.Success("המשמרות נשמרו בהצלחה!")
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
}

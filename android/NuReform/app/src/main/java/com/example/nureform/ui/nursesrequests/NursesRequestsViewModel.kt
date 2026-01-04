package com.example.nureform.ui.nursesrequests
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.repository.ShiftsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class NursesRequestsViewModel(
    private val shiftsRepository: ShiftsRepository
) : ViewModel() {
    private val _state = MutableStateFlow<NursesRequestsState>(NursesRequestsState.Loading)
    val state: StateFlow<NursesRequestsState> = _state.asStateFlow()
    fun loadNursesRequests() {
        viewModelScope.launch {
            _state.value = NursesRequestsState.Loading
            try {
                val result = shiftsRepository.getAllNursesShiftRequests()
                if (result.isSuccess) {
                    val requests = result.getOrNull() ?: emptyList()
                    val weekNumber = shiftsRepository.getCurrentWeekNumber()
                    val year = shiftsRepository.getCurrentYear()
                    val weekInfo = "שבוע $weekNumber, $year"
                    _state.value = NursesRequestsState.Success(requests, weekInfo)
                } else {
                    _state.value = NursesRequestsState.Error(
                        result.exceptionOrNull()?.message ?: "שגיאה בטעינת בקשות"
                    )
                }
            } catch (e: Exception) {
                _state.value = NursesRequestsState.Error(e.message ?: "שגיאה לא צפויה")
            }
        }
    }
}

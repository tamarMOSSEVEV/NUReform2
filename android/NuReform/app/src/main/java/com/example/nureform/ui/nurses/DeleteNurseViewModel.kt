package com.example.nureform.ui.nurses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.repository.NursesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeleteNurseViewModel(private val nursesRepository: NursesRepository) : ViewModel() {

    private val _deleteNurseState = MutableStateFlow<DeleteNurseState>(DeleteNurseState.Idle)
    val deleteNurseState: StateFlow<DeleteNurseState> = _deleteNurseState.asStateFlow()

    fun checkNurseExists(idNumber: String) {
        // Validate ID Number
        if (idNumber.isEmpty()) {
            _deleteNurseState.value = DeleteNurseState.Error("תעודת זהות נדרשת", "idNumber")
            return
        }

        if (idNumber.length != 9 || !idNumber.all { it.isDigit() }) {
            _deleteNurseState.value = DeleteNurseState.Error("תעודת זהות חייבת להכיל 9 ספרות", "idNumber")
            return
        }

        _deleteNurseState.value = DeleteNurseState.Loading

        viewModelScope.launch {
            try {
                val result = nursesRepository.getNurseById(idNumber)

                if (result.isSuccess) {
                    val nurse = result.getOrNull()!!
                    _deleteNurseState.value = DeleteNurseState.NurseFound(nurse)
                } else {
                    _deleteNurseState.value = DeleteNurseState.NurseNotFound
                }
            } catch (e: Exception) {
                _deleteNurseState.value = DeleteNurseState.Error(e.message ?: "שגיאה לא צפויה")
            }
        }
    }

    fun deleteNurse(idNumber: String) {
        _deleteNurseState.value = DeleteNurseState.Loading

        viewModelScope.launch {
            try {
                val result = nursesRepository.deleteNurse(idNumber)

                if (result.isSuccess) {
                    _deleteNurseState.value = DeleteNurseState.DeleteSuccess("האחות נמחקה בהצלחה")
                } else {
                    _deleteNurseState.value = DeleteNurseState.Error(
                        result.exceptionOrNull()?.message ?: "שגיאה במחיקת אחות"
                    )
                }
            } catch (e: Exception) {
                _deleteNurseState.value = DeleteNurseState.Error(e.message ?: "שגיאה לא צפויה")
            }
        }
    }

    fun resetState() {
        _deleteNurseState.value = DeleteNurseState.Idle
    }
}


package com.example.nureform.ui.nurses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.repository.NursesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NursesListViewModel(
    private val nursesRepository: NursesRepository) : ViewModel() {

    private val _nursesListState = MutableStateFlow<NursesListState>(NursesListState.Idle)
    val nursesListState: StateFlow<NursesListState> = _nursesListState.asStateFlow()

    fun loadNurses() {
        _nursesListState.value = NursesListState.Loading

        viewModelScope.launch {
            try {
                val result = nursesRepository.getAllNurses()

                if (result.isSuccess) {
                    val nurses = result.getOrNull() ?: emptyList()
                    if (nurses.isEmpty()) {
                        _nursesListState.value = NursesListState.Empty
                    } else {
                        _nursesListState.value = NursesListState.Success(nurses)
                    }
                } else {
                    _nursesListState.value = NursesListState.Error(
                        result.exceptionOrNull()?.message ?: "שגיאה בטעינת רשימת אחיות"
                    )
                }
            } catch (e: Exception) {
                _nursesListState.value = NursesListState.Error(e.message ?: "שגיאה לא צפויה")
            }
        }
    }
}


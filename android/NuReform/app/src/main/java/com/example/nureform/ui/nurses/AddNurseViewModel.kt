package com.example.nureform.ui.nurses

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nureform.data.model.Nurse
import com.example.nureform.data.repository.NursesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddNurseViewModel(
    private val nursesRepository: NursesRepository,
) : ViewModel() {

    private val _addNurseState = MutableStateFlow<AddNurseState>(AddNurseState.Idle)
    val addNurseState: StateFlow<AddNurseState> = _addNurseState.asStateFlow()

    fun addNurse(idNumber: String, name: String, phone: String, email: String) {
        // Validate inputs
        if (!validateInputs(idNumber, name, phone, email)) {
            return
        }

        _addNurseState.value = AddNurseState.Loading

        viewModelScope.launch {
            try {
                val nurse = Nurse(
                    idNumber = idNumber,
                    name = name,
                    phone = phone,
                    email = email
                )

                val result = nursesRepository.addNurse(nurse)

                if (result.isSuccess) {
                    _addNurseState.value = AddNurseState.Success(nurse, "האחות נוספה בהצלחה")
                } else {
                    _addNurseState.value = AddNurseState.Error(
                        result.exceptionOrNull()?.message ?: "שגיאה בהוספת אחות"
                    )
                }
            } catch (e: Exception) {
                _addNurseState.value = AddNurseState.Error(e.message ?: "שגיאה לא צפויה")
            }
        }
    }

    private fun validateInputs(
        idNumber: String,
        name: String,
        phone: String,
        email: String
    ): Boolean {
        // Validate ID Number
        if (idNumber.isEmpty()) {
            _addNurseState.value = AddNurseState.Error("תעודת זהות נדרשת", "idNumber")
            return false
        }

        if (idNumber.length != 9 || !idNumber.all { it.isDigit() }) {
            _addNurseState.value = AddNurseState.Error("תעודת זהות חייבת להכיל 9 ספרות", "idNumber")
            return false
        }

        // Validate Name
        if (name.isEmpty()) {
            _addNurseState.value = AddNurseState.Error("שם נדרש", "name")
            return false
        }

        if (name.length < 2) {
            _addNurseState.value = AddNurseState.Error("שם חייב להכיל לפחות 2 תווים", "name")
            return false
        }

        // Validate Phone
        if (phone.isEmpty()) {
            _addNurseState.value = AddNurseState.Error("טלפון נדרש", "phone")
            return false
        }

        if (phone.length != 10 || !phone.all { it.isDigit() }) {
            _addNurseState.value = AddNurseState.Error("טלפון חייב להכיל 10 ספרות", "phone")
            return false
        }

        // Validate Email
        if (email.isEmpty()) {
            _addNurseState.value = AddNurseState.Error("מייל נדרש", "email")
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _addNurseState.value = AddNurseState.Error("כתובת מייל לא תקינה", "email")
            return false
        }

        return true
    }

    fun resetState() {
        _addNurseState.value = AddNurseState.Idle
    }
}


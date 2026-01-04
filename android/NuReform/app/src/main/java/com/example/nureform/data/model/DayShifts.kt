package com.example.nureform.data.model

data class DayShifts(
    val dayOfWeek: String, // "Sunday", "Monday", etc.
    val shifts: List<ShiftType> = emptyList()
)


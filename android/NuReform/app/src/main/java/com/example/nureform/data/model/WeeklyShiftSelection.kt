package com.example.nureform.data.model

data class WeeklyShiftSelection(
    val nurseId: String = "",
    val weekNumber: Int = 0,
    val year: Int = 0,
    val shifts: Map<String, List<String>> = emptyMap(), // Map of day to list of shift types
    val submittedAt: Long = 0L
)


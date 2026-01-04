package com.example.nureform.data.model

data class NurseShiftRequest(
    val nurseId: String = "",
    val nurseName: String = "",
    val hasSubmitted: Boolean = false,
    val shifts: Map<String, List<String>> = emptyMap() // Map of day to list of shift types
)


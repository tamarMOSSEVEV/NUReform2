package com.example.nureform.data.model

data class NurseSchedule(
    val nurseName: String,
    val sunday: String = "X",
    val monday: String = "X",
    val tuesday: String = "X",
    val wednesday: String = "X",
    val thursday: String = "X",
    val friday: String = "X",
    val saturday: String = "X"
)


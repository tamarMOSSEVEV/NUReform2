package com.example.nureform.utils

object ShiftConstants {
    // Days of the week (English keys for Firestore)
    const val SUNDAY = "Sunday"
    const val MONDAY = "Monday"
    const val TUESDAY = "Tuesday"
    const val WEDNESDAY = "Wednesday"
    const val THURSDAY = "Thursday"
    const val FRIDAY = "Friday"
    const val SATURDAY = "Saturday"

    // Days of the week with Hebrew labels
    val DAYS_OF_WEEK = listOf(
        SUNDAY to "יום ראשון",
        MONDAY to "יום שני",
        TUESDAY to "יום שלישי",
        WEDNESDAY to "יום רביעי",
        THURSDAY to "יום חמישי",
        FRIDAY to "יום שישי",
        SATURDAY to "יום שבת"
    )

    // Shift types
    const val SHIFT_MORNING = "morning"
    const val SHIFT_NOON = "noon"
    const val SHIFT_EVENING = "evening"
}


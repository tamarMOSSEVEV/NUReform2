package com.example.nureform.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class UserRole(val value: String) : Parcelable {
    NURSE("nurse"),
    MANAGER("manager");

    companion object {
        fun fromString(value: String): UserRole {
            return UserRole.entries.find { it.value.equals(value, ignoreCase = true) } ?: NURSE
        }
    }
}
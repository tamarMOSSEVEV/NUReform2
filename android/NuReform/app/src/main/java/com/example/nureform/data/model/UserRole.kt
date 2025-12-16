package com.example.nureform.data.model

enum class UserRole(val value: String) {
    NURSE("nurse"),
    MANAGER("manager");

    companion object {
        fun fromString(value: String): UserRole {
            return UserRole.entries.find { it.value.equals(value, ignoreCase = true) } ?: NURSE
        }
    }
}
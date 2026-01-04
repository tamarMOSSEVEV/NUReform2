package com.example.nureform.data.model

data class Nurse(
    val idNumber: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val userId: String = "", // Firebase Auth UID
    val createdAt: Long = System.currentTimeMillis()
)


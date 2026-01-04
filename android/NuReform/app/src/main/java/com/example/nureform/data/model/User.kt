package com.example.nureform.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val uid : String = "",
    val name: String = "",
    val role: UserRole,
) : Parcelable




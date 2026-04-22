package com.example.nureform.data.model

import com.google.gson.annotations.SerializedName

data class SchedulingResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("assignments")
    val assignments: List<NurseAssignment>? = null,

    @SerializedName("week")
    val week: String? = null,

    @SerializedName("message")
    val message: String? = null
)

data class NurseAssignment(
    @SerializedName("nurseId")
    val nurseId: String,

    @SerializedName("schedule")
    val schedule: Map<String, String>
)

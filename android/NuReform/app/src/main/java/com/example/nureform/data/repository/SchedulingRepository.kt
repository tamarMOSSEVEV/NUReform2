package com.example.nureform.data.repository

import com.example.nureform.data.api.RetrofitClient
import com.example.nureform.data.model.SchedulingResponse
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Calendar

sealed class ScheduleStatus {
    object Running : ScheduleStatus()
    object Success : ScheduleStatus()
    data class Error(val message: String) : ScheduleStatus()
}

class SchedulingRepository {
    private val apiService = RetrofitClient.schedulingApiService
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun runScheduling(): Result<SchedulingResponse> {
        return try {
            val response = apiService.runScheduling()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.status == "running" || body.status == "success") {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body.message ?: "Unknown error occurred"))
                }
            } else {
                Result.failure(Exception("Failed to run scheduling: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToScheduleStatus(): Flow<ScheduleStatus> = callbackFlow {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.minimalDaysInFirstWeek = 1
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val weekNumber = calendar.get(Calendar.WEEK_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        val weekDocId = "${year}_${weekNumber}"

        val listener = firestore.collection("nurses_shifts").document(weekDocId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ScheduleStatus.Error(error.message ?: "Firestore error"))
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    when (snapshot.getString("status")) {
                        "running" -> trySend(ScheduleStatus.Running)
                        "success" -> trySend(ScheduleStatus.Success)
                        "error" -> trySend(
                            ScheduleStatus.Error(
                                snapshot.getString("message") ?: "שגיאה בשיבוץ"
                            )
                        )
                    }
                }
            }

        awaitClose { listener.remove() }
    }
}

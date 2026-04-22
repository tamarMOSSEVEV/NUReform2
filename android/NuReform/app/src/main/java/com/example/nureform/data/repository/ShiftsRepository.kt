package com.example.nureform.data.repository
import com.example.nureform.data.model.NurseSchedule
import com.example.nureform.data.model.WeeklyShiftSelection
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar

const val WEEKLY_SHIFTS_SUBCOLLECTION = "weekly_shifts"
const val FINALIZED_SHIFTS_COLLECTION = "nurses_shifts"

class ShiftsRepository {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val nursesCollection = firestore.collection("nurses")
    private val shiftsCollection = firestore.collection(FINALIZED_SHIFTS_COLLECTION)

    fun getCurrentWeekNumber(): Int {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.minimalDaysInFirstWeek = 1
        return calendar.get(Calendar.WEEK_OF_YEAR)
    }
    fun getCurrentYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR)
    }

    /**
     * Returns the doc ID for next week (year_weekNumber).
     * Nurses choose shifts for next week, manager schedules for next week.
     */
    fun getNextWeekDocId(): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.minimalDaysInFirstWeek = 1
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        return "${year}_${week}"
    }

    fun getNextWeekNumber(): Int {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.minimalDaysInFirstWeek = 1
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        return calendar.get(Calendar.WEEK_OF_YEAR)
    }

    fun getNextWeekYear(): Int {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.minimalDaysInFirstWeek = 1
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        return calendar.get(Calendar.YEAR)
    }
    fun isSubmissionWindowOpen(): Boolean {
        // TODO: For testing - always return true. In production, enable the date checking below
        return true

        // Production code (Sunday 00:00 to Monday 23:59):

//        val calendar = Calendar.getInstance()
//        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
//        return when (dayOfWeek) {
//            Calendar.SUNDAY -> true
//            Calendar.MONDAY -> true
//            else -> false
//        }

    }
    suspend fun hasSubmittedForNextWeek(nurseId: String): Result<Boolean> {
        return try {
            val documentId = getNextWeekDocId()

            // Access subcollection under the nurse's document
            val document = nursesCollection
                .document(nurseId)
                .collection(WEEKLY_SHIFTS_SUBCOLLECTION)
                .document(documentId)
                .get()
                .await()

            Result.success(document.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun areShiftsFinalized(): Result<Boolean> {
        return try {
            val documentId = getNextWeekDocId()

            val document = shiftsCollection
                .document(documentId)
                .get()
                .await()

            val isFinalized = document.exists() && document.getString("status") == "success"
            Result.success(isFinalized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToScheduleStatus(): Flow<String?> = callbackFlow {
        val documentId = getNextWeekDocId()

        val listener = shiftsCollection.document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.getString("status"))
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }
    suspend fun submitWeeklyShifts(
        nurseId: String,
        shifts: Map<String, List<String>>
    ): Result<WeeklyShiftSelection> {
        return try {
            val weekNumber = getNextWeekNumber()
            val year = getNextWeekYear()
            val documentId = getNextWeekDocId()

            val shiftSelection = WeeklyShiftSelection(
                nurseId = nurseId,
                weekNumber = weekNumber,
                year = year,
                shifts = shifts,
                submittedAt = System.currentTimeMillis()
            )

            // Save to subcollection under the nurse's document
            nursesCollection
                .document(nurseId)
                .collection(WEEKLY_SHIFTS_SUBCOLLECTION)
                .document(documentId)
                .set(shiftSelection)
                .await()

            Result.success(shiftSelection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getShiftsForNextWeek(nurseId: String): Result<WeeklyShiftSelection?> {
        return try {
            val documentId = getNextWeekDocId()

            // Get from subcollection under the nurse's document
            val document = nursesCollection
                .document(nurseId)
                .collection(WEEKLY_SHIFTS_SUBCOLLECTION)
                .document(documentId)
                .get()
                .await()

            if (document.exists()) {
                val selection = document.toObject(WeeklyShiftSelection::class.java)
                Result.success(selection)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllNursesShiftRequests(): Result<List<com.example.nureform.data.model.NurseShiftRequest>> {
        return try {
            val documentId = getNextWeekDocId()

            // Get all nurses
            val nursesSnapshot = nursesCollection.get().await()
            val nurseShiftRequests = mutableListOf<com.example.nureform.data.model.NurseShiftRequest>()

            for (nurseDoc in nursesSnapshot.documents) {
                val nurseId = nurseDoc.id
                val nurseName = nurseDoc.getString("name") ?: "Unknown"

                // Try to get the shift submission for this week
                val shiftDoc = nursesCollection
                    .document(nurseId)
                    .collection(WEEKLY_SHIFTS_SUBCOLLECTION)
                    .document(documentId)
                    .get()
                    .await()

                if (shiftDoc.exists()) {
                    val shiftSelection = shiftDoc.toObject(WeeklyShiftSelection::class.java)
                    nurseShiftRequests.add(
                        com.example.nureform.data.model.NurseShiftRequest(
                            nurseId = nurseId,
                            nurseName = nurseName,
                            hasSubmitted = true,
                            shifts = shiftSelection?.shifts ?: emptyMap()
                        )
                    )
                } else {
                    // Nurse hasn't submitted yet
                    nurseShiftRequests.add(
                        com.example.nureform.data.model.NurseShiftRequest(
                            nurseId = nurseId,
                            nurseName = nurseName,
                            hasSubmitted = false,
                            shifts = emptyMap()
                        )
                    )
                }
            }

            Result.success(nurseShiftRequests)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getFinalizedSchedule(): Result<List<NurseSchedule>> {
        val documentId = "${getCurrentYear()}_${getCurrentWeekNumber()}"
        return getScheduleForWeek(documentId)
    }

    /**
     * Returns the status of a specific week's schedule doc: null, "running", "success", "error"
     */
    suspend fun getWeekStatus(weekDocId: String): String? {
        return try {
            val document = shiftsCollection.document(weekDocId).get().await()
            if (document.exists()) document.getString("status") else null
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getScheduleForWeek(weekDocId: String): Result<List<NurseSchedule>> {
        return try {
            val document = shiftsCollection
                .document(weekDocId)
                .get()
                .await()

            if (!document.exists() || document.getString("status") != "success") {
                return Result.success(emptyList())
            }

            val assignments = document.get("assignments") as? List<Map<String, Any>> ?: emptyList()
            val schedules = assignments.map { assignment ->
                NurseSchedule(
                    nurseName = assignment["nurseName"] as? String ?: "",
                    sunday = assignment["sunday"] as? String ?: "X",
                    monday = assignment["monday"] as? String ?: "X",
                    tuesday = assignment["tuesday"] as? String ?: "X",
                    wednesday = assignment["wednesday"] as? String ?: "X",
                    thursday = assignment["thursday"] as? String ?: "X",
                    friday = assignment["friday"] as? String ?: "X",
                    saturday = assignment["saturday"] as? String ?: "X"
                )
            }

            Result.success(schedules)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

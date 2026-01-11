package com.example.nureform.data.repository
import com.example.nureform.data.model.WeeklyShiftSelection
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

const val WEEKLY_SHIFTS_SUBCOLLECTION = "weekly_shifts"
const val FINALIZED_SHIFTS_COLLECTION = "shifts"

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
    suspend fun hasSubmittedForCurrentWeek(nurseId: String): Result<Boolean> {
        return try {
            val weekNumber = getCurrentWeekNumber()
            val year = getCurrentYear()
            val documentId = "${year}_${weekNumber}"

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
            val weekNumber = getCurrentWeekNumber()
            val year = getCurrentYear()
            val documentId = "${year}_${weekNumber}"

            // Check if the finalized shifts document exists
            val document = shiftsCollection
                .document(documentId)
                .get()
                .await()

            Result.success(document.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun submitWeeklyShifts(
        nurseId: String,
        shifts: Map<String, List<String>>
    ): Result<WeeklyShiftSelection> {
        return try {
            val weekNumber = getCurrentWeekNumber()
            val year = getCurrentYear()
            val documentId = "${year}_${weekNumber}"

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
    suspend fun getShiftsForCurrentWeek(nurseId: String): Result<WeeklyShiftSelection?> {
        return try {
            val weekNumber = getCurrentWeekNumber()
            val year = getCurrentYear()
            val documentId = "${year}_${weekNumber}"

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
            val weekNumber = getCurrentWeekNumber()
            val year = getCurrentYear()
            val documentId = "${year}_${weekNumber}"

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
}

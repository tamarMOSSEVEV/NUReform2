package com.example.nureform.data.repository

import com.example.nureform.data.model.Nurse
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

const val NURSES_COLLECTION = "nurses"

class NursesRepository {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val nursesCollection = firestore.collection(NURSES_COLLECTION)

    suspend fun addNurse(nurse: Nurse): Result<Nurse> {
        return try {
            // Use ID number as document ID for easy lookup
            nursesCollection.document(nurse.idNumber)
                .set(nurse)
                .await()

            Result.success(nurse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNurseById(idNumber: String): Result<Nurse> {
        return try {
            val document = nursesCollection.document(idNumber).get().await()

            if (document.exists()) {
                val nurse = document.toObject(Nurse::class.java)
                if (nurse != null) {
                    Result.success(nurse)
                } else {
                    Result.failure(Exception("לא ניתן להמיר את הנתונים"))
                }
            } else {
                Result.failure(Exception("אחות לא נמצאה"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllNurses(): Result<List<Nurse>> {
        return try {
            val snapshot = nursesCollection.get().await()
            val nurses = snapshot.documents.mapNotNull { it.toObject(Nurse::class.java) }
            Result.success(nurses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteNurse(idNumber: String): Result<Unit> {
        return try {
            nursesCollection.document(idNumber).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNurse(nurse: Nurse): Result<Nurse> {
        return try {
            nursesCollection.document(nurse.idNumber)
                .set(nurse)
                .await()

            Result.success(nurse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


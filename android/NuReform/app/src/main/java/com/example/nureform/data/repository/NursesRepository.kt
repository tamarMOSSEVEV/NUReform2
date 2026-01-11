package com.example.nureform.data.repository

import com.example.nureform.data.model.Nurse
import com.example.nureform.data.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

const val NURSES_COLLECTION = "nurses"

class NursesRepository {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val nursesCollection = firestore.collection(NURSES_COLLECTION)
    private val usersCollection = firestore.collection(Constants.COLLECTION_USERS)

    suspend fun addNurse(nurse: Nurse, password: String): Result<Nurse> {
        var secondaryAuth: FirebaseAuth? = null
        return try {
            // Verify user is authenticated
            if (auth.currentUser == null) {
                return Result.failure(Exception("יש להתחבר כמנהל כדי להוסיף אחות"))
            }

            // Create a secondary FirebaseApp instance for creating the nurse account
            // This allows us to create a user without affecting the current session
            val firebaseDefaultApp = auth.app
            val signUpAppName = "${firebaseDefaultApp.name}_signUp"

            val signUpApp = try {
                FirebaseApp.initializeApp(
                    firebaseDefaultApp.applicationContext,
                    firebaseDefaultApp.options,
                    signUpAppName
                )
            } catch (e: IllegalStateException) {
                // IllegalStateException is thrown if an app with the same name has already been initialized
                FirebaseApp.getInstance(signUpAppName)
            }

            // Get the secondary auth instance that won't affect the current user's session
            secondaryAuth = FirebaseAuth.getInstance(signUpApp)

            // Step 1: Register user with Firebase Authentication using secondary instance
            val authResult = secondaryAuth.createUserWithEmailAndPassword(
                /* p0 = */ nurse.email,
                /* p1 = */ password,
            ).await()
            val userId = authResult.user?.uid
                ?: return Result.failure(Exception("נכשל ביצירת משתמש"))

            // Step 2: Create user document in "users" collection
            val userData = hashMapOf(
                Constants.FIELD_NAME to nurse.name,
                Constants.FIELD_ROLE to UserRole.NURSE.value,
            )

            usersCollection.document(userId)
                .set(userData)
                .await()

            // Step 3: Create nurse document in "nurses" collection
            val nurseWithUid = nurse.copy(userId = userId)
            nursesCollection.document(userId)
                .set(nurseWithUid)
                .await()

            // Step 4: Sign out from secondary auth instance
            secondaryAuth.signOut()

            Result.success(nurseWithUid)
        } catch (e: Exception) {
            secondaryAuth?.signOut()
            Result.failure(e)
        }
    }

    suspend fun getNurseById(idNumber: String): Result<Nurse> {
        return try {
            // Query to find the nurse by idNumber field
            val snapshot = nursesCollection
                .whereEqualTo("idNumber", idNumber)
                .get()
                .await()

            if (snapshot.isEmpty) {
                return Result.failure(Exception("אחות לא נמצאה"))
            }

            val nurse = snapshot.documents.first().toObject(Nurse::class.java)
            if (nurse != null) {
                Result.success(nurse)
            } else {
                Result.failure(Exception("לא ניתן להמיר את הנתונים"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllNurses(): Result<List<Nurse>> {
        return try {
            val snapshot = nursesCollection.get().await()
            val nurses = snapshot.documents.mapNotNull {
                it.toObject(Nurse::class.java)
            }
            Result.success(nurses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteNurse(idNumber: String): Result<Unit> {
        return try {
            // Query to find the nurse by idNumber field
            val snapshot = nursesCollection
                .whereEqualTo("idNumber", idNumber)
                .get()
                .await()

            if (snapshot.isEmpty) {
                return Result.failure(Exception("אחות לא נמצאה"))
            }

            // Get the document ID (which is the userId/UUID)
            val nurseDocument = snapshot.documents.first()
            val userId = nurseDocument.id

            // Delete the nurse document using userId
            nursesCollection.document(userId).delete().await()

            // Also delete from users collection
            usersCollection.document(userId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNurse(nurse: Nurse): Result<Nurse> {
        return try {
            // Use userId as the document ID
            if (nurse.userId.isEmpty()) {
                return Result.failure(Exception("מזהה משתמש חסר"))
            }

            nursesCollection.document(nurse.userId)
                .set(nurse)
                .await()

            Result.success(nurse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


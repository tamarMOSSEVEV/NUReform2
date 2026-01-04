package com.example.nureform.data.repository

import com.example.nureform.data.model.User
import com.example.nureform.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun getCurrentUser() = auth.currentUser

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: throw Exception("User ID is null")
            val user = fetchUserData(userId)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserData(userId: String): User {
        val document = firestore.collection(Constants.COLLECTION_USERS)
            .document(userId)
            .get()
            .await()

        return if (document.exists()) {
            User(
                uid = document.getString(Constants.FIELD_UID) ?: userId,
                name = document.getString(Constants.FIELD_NAME) ?: "User",
                role = UserRole.fromString(document.getString(Constants.FIELD_ROLE) ?: "nurse"),
            )
        } else {
            throw Exception("User document not found")
        }
    }

    fun logout() {
        auth.signOut()
    }
}


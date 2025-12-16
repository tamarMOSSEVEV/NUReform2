package com.example.nureform.utils

object Constants {
    // Firebase Collections
    const val COLLECTION_USERS = "users"

    // Firebase Fields
    const val FIELD_UID = "uid"
    const val FIELD_EMAIL = "email"
    const val FIELD_NAME = "name"
    const val FIELD_ROLE = "role"
    const val FIELD_CREATED_AT = "createdAt"

    // Validation
    const val MIN_PASSWORD_LENGTH = 6

    // Messages
    const val ERROR_EMAIL_REQUIRED = "Email is required"
    const val ERROR_PASSWORD_REQUIRED = "Password is required"
    const val ERROR_PASSWORD_LENGTH = "Password must be at least 6 characters"
    const val SUCCESS_REGISTRATION = "Registration successful!"
    const val SUCCESS_LOGIN_PREFIX = "Welcome back, "
}


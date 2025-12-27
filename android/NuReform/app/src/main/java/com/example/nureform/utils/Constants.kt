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

    // Error Messages - Now using string resource references
    const val ERROR_EMAIL_REQUIRED = "error_email_required"
    const val ERROR_PASSWORD_REQUIRED = "error_password_required"
    const val ERROR_PASSWORD_LENGTH = "error_password_length"
    const val SUCCESS_REGISTRATION = "Registration successful!"
    const val SUCCESS_LOGIN_PREFIX = "success_login"
}


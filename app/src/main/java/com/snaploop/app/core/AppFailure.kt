package com.snaploop.app.core

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException

sealed class SnapLoopException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotAuthenticated(cause: Throwable? = null) : SnapLoopException("Authentication is required", cause)
    class EventNotFound(cause: Throwable? = null) : SnapLoopException("Event not found", cause)
    class EventFull(cause: Throwable? = null) : SnapLoopException("Event is full", cause)
    class EventExpired(cause: Throwable? = null) : SnapLoopException("Event has ended or expired", cause)
    class InvalidJoinCode(cause: Throwable? = null) : SnapLoopException("Invalid join code", cause)
    class PermissionDenied(cause: Throwable? = null) : SnapLoopException("Permission denied", cause)
    class InvalidData(message: String, cause: Throwable? = null) : SnapLoopException(message, cause)
    class Backend(val code: String, message: String, cause: Throwable? = null) : SnapLoopException(message, cause)
}

object FirebaseErrorMapper {
    fun functions(error: Throwable): SnapLoopException {
        val e = error as? FirebaseFunctionsException
            ?: return SnapLoopException.Backend("function_unknown", error.message ?: "Cloud Function failed", error)
        return when (e.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> SnapLoopException.NotAuthenticated(e)
            FirebaseFunctionsException.Code.NOT_FOUND -> SnapLoopException.EventNotFound(e)
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> SnapLoopException.EventFull(e)
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> SnapLoopException.PermissionDenied(e)
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {
                if (e.message.orEmpty().contains("join code", ignoreCase = true)) SnapLoopException.InvalidJoinCode(e)
                else SnapLoopException.Backend("invalid_argument", e.message ?: "Invalid request", e)
            }
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                if (e.message.orEmpty().contains("ended", ignoreCase = true) || e.message.orEmpty().contains("expired", ignoreCase = true)) {
                    SnapLoopException.EventExpired(e)
                } else SnapLoopException.Backend("failed_precondition", e.message ?: "Request cannot be completed", e)
            }
            else -> SnapLoopException.Backend("function_${e.code.name.lowercase()}", e.message ?: "Cloud Function failed", e)
        }
    }

    fun firestore(error: Throwable): SnapLoopException {
        val e = error as? FirebaseFirestoreException
            ?: return SnapLoopException.Backend("firestore_unknown", error.message ?: "Firestore request failed", error)
        return when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> SnapLoopException.PermissionDenied(e)
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> SnapLoopException.NotAuthenticated(e)
            FirebaseFirestoreException.Code.NOT_FOUND -> SnapLoopException.EventNotFound(e)
            else -> SnapLoopException.Backend("firestore_${e.code.name.lowercase()}", e.message ?: "Firestore request failed", e)
        }
    }
}

package com.snaploop.app.core

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Minimal coroutine bridge so Firebase access stays explicit and does not depend on KTX task adapters. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            task.isSuccessful -> continuation.resume(task.result)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase task failed without an exception"))
        }
    }
}

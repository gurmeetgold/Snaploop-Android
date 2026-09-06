package com.snaploop.app.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.snaploop.app.core.SnapLoopException
import com.snaploop.app.core.awaitResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface PhoneVerificationStart {
    data class CodeSent(val verificationId: String) : PhoneVerificationStart
    data class AutoVerified(val userId: String) : PhoneVerificationStart
}

/** Firebase phone authentication without logging phone numbers, verification IDs, or OTP values. */
class FirebasePhoneAuthService(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val currentUserId: String? get() = auth.currentUser?.uid

    suspend fun startPhoneVerification(activity: Activity, phoneNumberE164: String): PhoneVerificationStart {
        if (!phoneNumberE164.startsWith("+") || phoneNumberE164.length !in 8..16 || phoneNumberE164.drop(1).any { !it.isDigit() }) {
            throw SnapLoopException.InvalidData("Enter a valid phone number including country code")
        }

        return suspendCancellableCoroutine { continuation ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (!continuation.isActive) return
                    auth.signInWithCredential(credential).addOnCompleteListener { task ->
                        if (!continuation.isActive) return@addOnCompleteListener
                        if (task.isSuccessful) {
                            val uid = task.result?.user?.uid
                            if (uid != null) continuation.resume(PhoneVerificationStart.AutoVerified(uid))
                            else continuation.resumeWithException(SnapLoopException.Backend("auth_empty_user", "Verification succeeded without a user"))
                        } else continuation.resumeWithException(mapError(task.exception ?: FirebaseException("Phone verification failed")))
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (continuation.isActive) continuation.resumeWithException(mapError(e))
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    if (continuation.isActive) continuation.resume(PhoneVerificationStart.CodeSent(verificationId))
                }
            }

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumberE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    suspend fun confirmVerification(verificationId: String, code: String): String {
        if (code.length !in 4..8 || code.any { !it.isDigit() }) throw SnapLoopException.InvalidData("Enter the verification code")
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            auth.signInWithCredential(credential).awaitResult().user?.uid
                ?: throw SnapLoopException.Backend("auth_empty_user", "Sign-in succeeded without a user")
        } catch (e: SnapLoopException) { throw e }
          catch (t: Throwable) { throw mapError(t) }
    }

    fun signOut() = auth.signOut()

    private fun mapError(error: Throwable): SnapLoopException = when (error) {
        is FirebaseTooManyRequestsException -> SnapLoopException.Backend("too_many_requests", "Too many attempts. Please wait a bit and try again.", error)
        is FirebaseAuthInvalidCredentialsException -> SnapLoopException.InvalidData("The phone number or verification code is invalid", error)
        else -> SnapLoopException.Backend("phone_auth_failed", "Phone verification couldn't complete. Please try again.", error)
    }
}

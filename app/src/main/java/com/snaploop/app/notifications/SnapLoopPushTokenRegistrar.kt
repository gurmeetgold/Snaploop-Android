package com.snaploop.app.notifications

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.snaploop.app.BuildConfig
import com.snaploop.app.data.FirebaseCallableClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Mirrors the pinned iOS `registerPushToken` callable contract.
 *
 * FCM tokens are never logged or persisted by this client. Registration is
 * de-duplicated only for the lifetime of the process and is keyed by user ID so
 * switching accounts cannot inherit another account's registration state.
 */
object SnapLoopPushTokenRegistrar {
    private val mutex = Mutex()
    private val bestEffortScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastRegistrationKey: String? = null

    suspend fun registerCurrentToken(context: Context) {
        if (!BuildConfig.FIREBASE_CONFIG_PRESENT) return
        val token = FirebaseMessaging.getInstance().token.await()
        registerIfAuthenticated(context, token)
    }

    suspend fun registerIfAuthenticated(context: Context, token: String) {
        if (!BuildConfig.FIREBASE_CONFIG_PRESENT) return
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val registrationKey = "$uid:$cleanToken"

        mutex.withLock {
            if (lastRegistrationKey == registrationKey) return
            FirebaseCallableClient().call(
                name = "registerPushToken",
                data = mapOf(
                    "token" to cleanToken,
                    "platform" to "android",
                    "appBundleId" to context.applicationContext.packageName,
                ),
            )
            lastRegistrationKey = registrationKey
        }
    }

    /** Token refresh can arrive while no Activity exists; authenticated MAIN retries later. */
    fun registerBestEffort(context: Context, token: String) {
        val appContext = context.applicationContext
        bestEffortScope.launch {
            runCatching { registerIfAuthenticated(appContext, token) }
        }
    }
}

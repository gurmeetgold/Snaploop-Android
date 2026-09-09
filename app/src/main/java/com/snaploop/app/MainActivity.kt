package com.snaploop.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.core.InvitationResumeStore
import com.snaploop.app.ui.AppCoordinator
import com.snaploop.app.ui.SnapLoopDeepLinkEffect
import com.snaploop.app.ui.SnapLoopRoot
import com.snaploop.app.ui.SnapLoopTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single-activity host. Pending invite URLs and non-sensitive invitation resume
 * context are persisted until the coordinator consumes them so cold start,
 * authentication, Face Setup and process recreation retain the same invitation.
 */
class MainActivity : ComponentActivity() {
    private lateinit var coordinator: AppCoordinator
    private val pendingDeepLink = MutableStateFlow<Uri?>(null)
    private val invitePrefs by lazy { getSharedPreferences("snaploop.pending.invite", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InvitationResumeStore.initialize(applicationContext)
        coordinator = ViewModelProvider(this)[AppCoordinator::class.java]

        val launchUri = intent?.data
        val restored = InvitationResumeStore.state.value
        val launchRoute = launchUri?.let { DeepLinkParser.parse(it) }
        val launchMatchesRestored = restored != null && launchRoute != null &&
            launchRoute.token == restored.token &&
            launchRoute.code == restored.code &&
            launchRoute.action == restored.action

        when {
            InvitationResumeStore.hasRestoredForResolution() &&
                (launchUri == null || launchMatchesRestored) -> {
                // The durable resume context is authoritative after process
                // recreation. Drop a duplicate raw URL so Root resolves it once.
                invitePrefs.edit().remove(KEY_PENDING_URL).apply()
                pendingDeepLink.value = null
            }

            InvitationResumeStore.hasRestoredForResolution() && launchUri != null -> {
                // A genuinely new explicit link wins over an older interrupted
                // invitation rather than inheriting stale action provenance.
                InvitationResumeStore.clear()
                capturePendingDeepLink(launchUri)
            }

            else -> {
                capturePendingDeepLink(launchUri)
                if (pendingDeepLink.value == null) {
                    invitePrefs.getString(KEY_PENDING_URL, null)
                        ?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
                        ?.let { pendingDeepLink.value = it }
                }
            }
        }

        setContent {
            SnapLoopTheme {
                val deepLink by pendingDeepLink.collectAsState()
                SnapLoopDeepLinkEffect(
                    uri = deepLink,
                    coordinator = coordinator,
                    onConsumed = ::clearPendingDeepLink,
                )
                SnapLoopRoot(
                    activity = this@MainActivity,
                    coordinator = coordinator,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let {
            InvitationResumeStore.clear()
            capturePendingDeepLink(it)
        }
    }

    private fun capturePendingDeepLink(uri: Uri?) {
        if (uri == null) return
        invitePrefs.edit().putString(KEY_PENDING_URL, uri.toString()).apply()
        pendingDeepLink.value = uri
    }

    private fun clearPendingDeepLink() {
        invitePrefs.edit().remove(KEY_PENDING_URL).apply()
        pendingDeepLink.value = null
    }

    private companion object {
        const val KEY_PENDING_URL = "pending_url"
    }
}

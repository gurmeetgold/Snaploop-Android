package com.snaploop.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.snaploop.app.ui.AppCoordinator
import com.snaploop.app.ui.SnapLoopDeepLinkEffect
import com.snaploop.app.ui.SnapLoopRoot
import com.snaploop.app.ui.SnapLoopTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private lateinit var coordinator: AppCoordinator
    private val pendingDeepLink = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coordinator = ViewModelProvider(this)[AppCoordinator::class.java]
        pendingDeepLink.value = intent?.data

        setContent {
            SnapLoopTheme {
                val deepLink by pendingDeepLink.collectAsState()
                SnapLoopDeepLinkEffect(
                    uri = deepLink,
                    coordinator = coordinator,
                    onConsumed = { pendingDeepLink.value = null },
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
        pendingDeepLink.value = intent.data
    }
}

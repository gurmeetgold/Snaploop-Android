package com.snaploop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snaploop.app.ui.AppCoordinator
import com.snaploop.app.ui.SnapLoopApp
import com.snaploop.app.ui.SnapLoopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnapLoopTheme {
                val coordinator: AppCoordinator = viewModel()
                SnapLoopApp(
                    activity = this@MainActivity,
                    coordinator = coordinator,
                )
            }
        }
    }
}

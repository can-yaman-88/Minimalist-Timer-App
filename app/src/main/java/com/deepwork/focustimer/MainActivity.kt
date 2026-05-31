package com.deepwork.focustimer

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.deepwork.focustimer.ui.data.DataScreen
import com.deepwork.focustimer.ui.stats.StatsScreen
import com.deepwork.focustimer.ui.theme.FocusTimerTheme
import com.deepwork.focustimer.ui.theme.PitchBlack
import com.deepwork.focustimer.ui.theme.Sepia
import com.deepwork.focustimer.ui.theme.SepiaDim
import com.deepwork.focustimer.ui.timer.TimerScreen
import com.deepwork.focustimer.ui.timer.TimerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge-to-edge so the immersive timer can use the whole screen.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FocusTimerTheme {
                FocusApp()
            }
        }
    }
}

private enum class Tab { TIMER, STATS, DATA }

@Composable
private fun FocusApp() {
    val timerViewModel: TimerViewModel = viewModel()
    val immersive by timerViewModel.immersive.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(Tab.TIMER.ordinal) }

    RequestNotificationPermission()
    ImmersiveController(immersive)

    Scaffold(
        containerColor = PitchBlack,
        bottomBar = {
            // The system chrome and our own nav bar both vanish in immersive mode.
            if (!immersive) {
                NavigationBar(containerColor = PitchBlack) {
                    NavigationBarItem(
                        selected = tab == Tab.TIMER.ordinal,
                        onClick = { tab = Tab.TIMER.ordinal },
                        icon = { Icon(Icons.Filled.Timer, contentDescription = "Timer") },
                        label = { Text("Timer") },
                        colors = sepiaNavColors(),
                    )
                    NavigationBarItem(
                        selected = tab == Tab.STATS.ordinal,
                        onClick = { tab = Tab.STATS.ordinal },
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = "Daily Stats") },
                        label = { Text("Daily Stats") },
                        colors = sepiaNavColors(),
                    )
                    NavigationBarItem(
                        selected = tab == Tab.DATA.ordinal,
                        onClick = { tab = Tab.DATA.ordinal },
                        icon = { Icon(Icons.Filled.Storage, contentDescription = "Data") },
                        label = { Text("Data") },
                        colors = sepiaNavColors(),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TIMER.ordinal -> TimerScreen(timerViewModel)
                Tab.STATS.ordinal -> StatsScreen()
                Tab.DATA.ordinal -> DataScreen()
            }
        }
    }
}

@Composable
private fun sepiaNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = PitchBlack,
    selectedTextColor = Sepia,
    indicatorColor = Sepia,
    unselectedIconColor = SepiaDim,
    unselectedTextColor = SepiaDim,
)

/** Hide/show the status + navigation bars in response to the immersive toggle. */
@Composable
private fun ImmersiveController(immersive: Boolean) {
    val view = LocalView.current
    LaunchedEffect(immersive) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersive) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** Ask for POST_NOTIFICATIONS on Android 13+ so the live countdown can show. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored: the timer still runs, just without a visible notification */ }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

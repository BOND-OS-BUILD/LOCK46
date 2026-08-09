package com.lock46.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lock46.app.Graph
import com.lock46.app.enforce.DutyController
import com.lock46.app.enforce.Enforcer
import com.lock46.app.ui.theme.Lock46Colors
import com.lock46.app.ui.theme.Lock46Theme
import com.lock46.app.util.Permissions
import com.lock46.app.util.RequiredPermission

/** Screens in the app. Navigation is a single state value — V1 has no deep links. */
private enum class Route { ONBOARDING, HOME, APPS, SETTINGS }

/**
 * The only Activity in LOCK46.
 *
 * The blocking screen is a system overlay owned by the enforcement service, not an
 * Activity, so nothing here is on the enforcement path — this class is purely the
 * user-facing console.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Graph.ensure(applicationContext)
        Enforcer.init(applicationContext)
        // Reconcile persisted state on every launch: the process may have been killed
        // while on duty, or duty may have elapsed while the app was not running.
        DutyController.restore(applicationContext)

        setContent {
            Lock46Theme {
                Lock46Root()
            }
        }
    }
}

@Composable
private fun Lock46Root() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var route by remember {
        mutableStateOf(
            if (Graph.settings.isSetupComplete) Route.HOME else Route.ONBOARDING
        )
    }
    // Bumped on every resume so permission state is re-read after the user returns from
    // an Android settings page.
    var refreshKey by remember { mutableIntStateOf(0) }
    var showChangePin by remember { mutableStateOf(false) }
    var returnToAfterApps by remember { mutableStateOf(Route.HOME) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                Graph.duty.refresh()
                Graph.whitelist.reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openSystemSetting: (RequiredPermission) -> Unit = { permission ->
        if (permission == RequiredPermission.NOTIFICATIONS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Permissions.hasNotificationPermission(context)
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(Permissions.settingsIntentFor(context, permission))
            }
        }
    }

    BackHandler(enabled = route != Route.HOME) {
        route = if (route == Route.APPS) returnToAfterApps else Route.HOME
    }

    Scaffold(
        containerColor = Lock46Colors.Ink,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (route) {
                Route.ONBOARDING -> OnboardingScreen(
                    refreshKey = refreshKey,
                    onOpenSystemSetting = openSystemSetting,
                    onOpenEssentialApps = {
                        returnToAfterApps = Route.ONBOARDING
                        route = Route.APPS
                    },
                    onFinish = { route = Route.HOME }
                )

                Route.HOME -> HomeScreen(
                    onOpenEssentialApps = {
                        returnToAfterApps = Route.HOME
                        route = Route.APPS
                    },
                    onOpenSettings = { route = Route.SETTINGS }
                )

                Route.APPS -> AppPickerScreen(
                    onDone = { route = returnToAfterApps }
                )

                Route.SETTINGS -> SettingsScreen(
                    refreshKey = refreshKey,
                    onOpenEssentialApps = {
                        returnToAfterApps = Route.SETTINGS
                        route = Route.APPS
                    },
                    onChangePin = { showChangePin = true },
                    onOpenSystemSetting = openSystemSetting,
                    onBack = { route = Route.HOME }
                )
            }
        }
    }

    if (showChangePin) {
        ChangePinDialog(onDismiss = { showChangePin = false })
    }
}

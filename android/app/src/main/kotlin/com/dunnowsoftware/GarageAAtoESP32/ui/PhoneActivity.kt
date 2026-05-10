package com.dunnowsoftware.GarageAAtoESP32.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.dunnowsoftware.GarageAAtoESP32.DemoOpener
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.ui.screens.*
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneActivity : ComponentActivity() {

    private lateinit var prefs: DevicePreferences
    private val bleManager by lazy { GarageBleManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force the system bars (status + nav) to light icons against our
        // dark app background. Defaults follow system theme, which renders
        // dark icons on light-mode devices and makes them invisible.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        prefs = DevicePreferences(this)
        setContent {
            GarageTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors.Bg)
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    AppRoot(
                        prefs = prefs,
                        onTriggerOpen = ::triggerOpen,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.cleanup()
    }

    private fun triggerOpen(onResult: (OpenResult) -> Unit) {
        if (prefs.demoMode) {
            android.os.Handler(mainLooper).postDelayed(
                { onResult(DemoOpener.nextResult()) },
                DemoOpener.DELAY_MS,
            )
            return
        }
        val address = prefs.deviceAddress
        if (address == null) {
            onResult(OpenResult.Failure("No paired device"))
            return
        }
        bleManager.connectAndOpen(address, prefs.pin) { result ->
            runOnUiThread { onResult(result) }
        }
    }
}

private sealed interface Route {
    data object Welcome      : Route
    data object SetPassword  : Route
    data object Scan         : Route
    data object Main         : Route
    data object Settings     : Route
    data object ChangePassword : Route
}

@Composable
private fun AppRoot(
    prefs: DevicePreferences,
    onTriggerOpen: ((OpenResult) -> Unit) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // Re-snapshot pref state into Compose state on each route change so the UI
    // reflects edits made on other screens.
    var routeStack by rememberSaveable(stateSaver = routeStackSaver) {
        mutableStateOf(initialStack(prefs))
    }
    var stateBust by remember { mutableIntStateOf(0) }
    val current = routeStack.last()

    fun push(r: Route) { routeStack = routeStack + r }
    fun pop() {
        if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
    }
    fun replaceAll(r: Route) { routeStack = listOf(r) }
    fun bust() { stateBust++ }

    when (current) {
        Route.Welcome -> WelcomeScreen(
            onGetStarted = { push(Route.SetPassword) },
        )

        Route.SetPassword -> SetPasswordScreen(
            initialPassword = prefs.pin,
            onSave = { pwd ->
                prefs.pin = pwd
                bust()
                if (prefs.hasPairedDevice) replaceAll(Route.Main) else push(Route.Scan)
            },
            onBack = if (routeStack.size > 1) ({ pop() }) else null,
        )

        Route.ChangePassword -> SetPasswordScreen(
            initialPassword = prefs.pin,
            onSave = { pwd ->
                prefs.pin = pwd
                bust()
                pop()
                Toast.makeText(ctx, "Password updated", Toast.LENGTH_SHORT).show()
            },
            onBack = { pop() },
        )

        Route.Scan -> ScanScreen(
            onPicked = { dev ->
                prefs.deviceAddress = dev.address
                prefs.deviceName = dev.name
                bust()
                replaceAll(Route.Main)
            },
            onBack = if (routeStack.size > 1) ({ pop() }) else null,
        )

        Route.Main -> MainHost(
            prefs = prefs,
            stateBust = stateBust,
            onTriggerOpen = onTriggerOpen,
            onSettings = { push(Route.Settings) },
        )

        Route.Settings -> SettingsScreen(
            deviceName = remember(stateBust) { prefs.deviceName },
            deviceAddress = remember(stateBust) { prefs.deviceAddress },
            demoMode = remember(stateBust) { prefs.demoMode },
            onBack = { pop() },
            onChangePassword = { push(Route.ChangePassword) },
            onRepair = { push(Route.Scan) },
            onUnpair = {
                prefs.unpairDevice()
                bust()
                replaceAll(Route.Scan)
            },
            onToggleDemo = { v ->
                prefs.demoMode = v
                bust()
            },
        )
    }
}

@Composable
private fun MainHost(
    prefs: DevicePreferences,
    stateBust: Int,
    onTriggerOpen: ((OpenResult) -> Unit) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var openState by remember { mutableStateOf(OpenState.Idle) }
    var lastOpened by remember(stateBust) { mutableLongStateOf(prefs.lastOpenedAt) }

    // After a terminal celebration state (Opened/Failed), settle back to Idle.
    LaunchedEffect(openState) {
        if (openState == OpenState.Opened || openState == OpenState.Failed) {
            delay(2000)
            openState = OpenState.Idle
        }
    }

    val deviceLabel = remember(stateBust) {
        when {
            prefs.demoMode               -> "Demo mode"
            prefs.deviceName != null     -> "ESP32 · ${prefs.deviceName}"
            prefs.deviceAddress != null  -> "ESP32 · ${prefs.deviceAddress}"
            else                         -> "Not configured"
        }
    }

    val presence = rememberPresence(prefs, stateBust)

    MainScreen(
        deviceLabel = deviceLabel,
        state = openState,
        presence = presence,
        lastOpenedLabel = lastOpened.takeIf { it > 0 }?.let { formatTime(it) },
        onSettings = onSettings,
        onOpen = {
            if (openState != OpenState.Idle) return@MainScreen
            openState = OpenState.Sending
            onTriggerOpen { result ->
                when (result) {
                    is OpenResult.Success -> {
                        prefs.lastOpenedAt = System.currentTimeMillis()
                        lastOpened = prefs.lastOpenedAt
                        openState = OpenState.Opened
                    }
                    is OpenResult.Failure -> {
                        openState = OpenState.Failed
                        Toast.makeText(ctx, result.reason, Toast.LENGTH_LONG).show()
                    }
                }
            }
        },
    )
}

/**
 * Background presence scan: listens for advertisements from the paired MAC.
 * Returns InRange while the device is heard, OutOfRange after a few seconds
 * of silence. Demo mode short-circuits to InRange (no actual scan). Stops
 * scanning when the composable leaves composition (screen off / route change).
 */
@Composable
private fun rememberPresence(prefs: DevicePreferences, stateBust: Int): PresenceStatus {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val demo = remember(stateBust) { prefs.demoMode }
    val address = remember(stateBust) { prefs.deviceAddress }

    if (demo) return PresenceStatus.InRange
    if (address.isNullOrEmpty()) return PresenceStatus.NotPaired

    var lastSeenMs by remember(address) { mutableLongStateOf(0L) }
    var nowMs by remember(address) { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(address) {
        val scanner = com.dunnowsoftware.GarageAAtoESP32.ble.BleScanner(ctx)
        try {
            scanner.startPresence(address) { _ ->
                lastSeenMs = System.currentTimeMillis()
            }
        } catch (_: Throwable) {
            // Permissions not granted / BT off — leave lastSeenMs at 0 so
            // the UI shows OutOfRange. Don't crash the screen.
        }
        onDispose { scanner.stop() }
    }

    // Tick the clock every second so the staleness window evaluates without
    // needing a scan callback.
    LaunchedEffect(address) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    // 15s is generous: covers Android's LOW_POWER scan duty cycle (~5s on,
    // ~5s off) and the worst-case adv interval. Smaller windows cause the
    // dot to flicker between callbacks even when the device is stably in
    // range.
    val staleAfterMs = 15_000L
    return if (lastSeenMs > 0 && (nowMs - lastSeenMs) < staleAfterMs)
        PresenceStatus.InRange
    else
        PresenceStatus.OutOfRange
}

private fun initialStack(prefs: DevicePreferences): List<Route> = when {
    prefs.isConfigured     -> listOf(Route.Main)
    !prefs.hasPassword     -> listOf(Route.Welcome)
    else                   -> listOf(Route.Welcome, Route.SetPassword, Route.Scan)
}

private fun formatTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.US).run {
        format(Date(now)) == format(Date(epochMs))
    }
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    return if (sameDay) {
        "Today, ${timeFmt.format(Date(epochMs))}"
    } else {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(epochMs))
    }
}

private val routeStackSaver = androidx.compose.runtime.saveable.listSaver<List<Route>, String>(
    save = { it.map(::routeKey) },
    restore = { it.map(::keyRoute) },
)

private fun routeKey(r: Route): String = when (r) {
    Route.Welcome        -> "welcome"
    Route.SetPassword    -> "set_pwd"
    Route.ChangePassword -> "chg_pwd"
    Route.Scan           -> "scan"
    Route.Main           -> "main"
    Route.Settings       -> "settings"
}

private fun keyRoute(k: String): Route = when (k) {
    "welcome"  -> Route.Welcome
    "set_pwd"  -> Route.SetPassword
    "chg_pwd"  -> Route.ChangePassword
    "scan"     -> Route.Scan
    "settings" -> Route.Settings
    else       -> Route.Main
}

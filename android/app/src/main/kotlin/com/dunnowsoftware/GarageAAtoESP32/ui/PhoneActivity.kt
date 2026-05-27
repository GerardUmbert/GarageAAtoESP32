package com.dunnowsoftware.GarageAAtoESP32.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.dunnowsoftware.GarageAAtoESP32.DemoOpener
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.getSavedLocaleTag
import com.dunnowsoftware.GarageAAtoESP32.data.localeListFromTag
import com.dunnowsoftware.GarageAAtoESP32.data.saveLocaleTag
import androidx.core.content.FileProvider
import androidx.car.app.connection.CarConnection
import com.dunnowsoftware.GarageAAtoESP32.AndroidAutoState
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceLogger
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceManager
import com.dunnowsoftware.GarageAAtoESP32.ui.screens.*
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneActivity : AppCompatActivity() {

    private lateinit var prefs: DevicePreferences
    private val bleManager by lazy { GarageBleManager(this) }
    private val shortcutOpenPending = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        val savedTag = prefs.getString("selected_locale", null).takeIf { !it.isNullOrEmpty() }
        if (savedTag != null) {
            AppCompatDelegate.setApplicationLocales(localeListFromTag(savedTag))
        }
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        prefs = DevicePreferences(this)
        CarConnection(this).type.observe(this) { connectionType ->
            val connected = connectionType == CarConnection.CONNECTION_TYPE_PROJECTION
            AndroidAutoState.isConnected = connected
            GeofenceLogger.i(this, "PhoneActivity", "AA connection type=$connectionType connected=$connected")
        }
        if (intent?.getBooleanExtra("voice_open", false) == true) shortcutOpenPending.value = true
        setContent {
            GarageTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GarageColors.Bg)
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    AppRoot(
                        prefs = prefs,
                        onTriggerOpen = ::triggerOpen,
                        shortcutOpenPending = shortcutOpenPending,
                        onApplyLocale = { tag ->
                            saveLocaleTag(this@PhoneActivity, tag)
                            AppCompatDelegate.setApplicationLocales(localeListFromTag(tag))
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("voice_open", false)) shortcutOpenPending.value = true
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
        val paired = prefs.pairedDevice
        if (paired == null) {
            onResult(OpenResult.Failure("No paired device"))
            return
        }
        bleManager.connectAndOpen(paired.address, paired.password) { result ->
            runOnUiThread { onResult(result) }
        }
    }
}

private sealed interface Route {
    data object Welcome        : Route
    data object Scan           : Route
    data object Pair           : Route
    data object Main           : Route
    data object Settings       : Route
    data object ChangePassword : Route
    data object ScanAnother    : Route
    data object Language       : Route
    data object GeofencePicker : Route
}

@Composable
private fun AppRoot(
    prefs: DevicePreferences,
    onTriggerOpen: ((OpenResult) -> Unit) -> Unit,
    shortcutOpenPending: androidx.compose.runtime.MutableState<Boolean>,
    onApplyLocale: (String?) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var routeStack by rememberSaveable(stateSaver = routeStackSaver) {
        mutableStateOf(initialStack(prefs))
    }
    var stateBust by remember { mutableIntStateOf(0) }
    var pendingPair by remember { mutableStateOf<com.dunnowsoftware.GarageAAtoESP32.ble.FoundDevice?>(null) }
    val current = routeStack.last()

    fun push(r: Route) { routeStack = routeStack + r }
    fun pop() {
        if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
    }
    fun replaceAll(r: Route) { routeStack = listOf(r) }
    fun bust() { stateBust++ }

    BackHandler(enabled = routeStack.size > 1) { pop() }

    when (current) {
        Route.Welcome -> WelcomeScreen(
            onGetStarted = { push(Route.Scan) },
        )

        Route.ChangePassword -> SetPasswordScreen(
            initialPassword = prefs.pairedDevice?.password.orEmpty(),
            onSave = { pwd ->
                prefs.updatePairedPassword(pwd)
                bust()
                pop()
                Toast.makeText(ctx, ctx.getString(R.string.toast_password_updated), Toast.LENGTH_SHORT).show()
            },
            onBack = { pop() },
            title = ctx.getString(R.string.password_screen_change_title),
            description = ctx.getString(R.string.password_screen_change_description),
            saveLabel = ctx.getString(R.string.password_screen_change_save),
        )

        Route.Scan -> ScanScreen(
            onPicked = { dev ->
                pendingPair = dev
                push(Route.Pair)
            },
            onBack = if (routeStack.size > 1) ({ pop() }) else null,
            onSkip = { replaceAll(Route.Main) },
        )

        Route.ScanAnother -> ScanScreen(
            excludeAddress = remember(stateBust) { prefs.pairedDevice?.address },
            onPicked = { dev ->
                pendingPair = dev
                push(Route.Pair)
            },
            onBack = { pop() },
        )

        Route.Pair -> {
            val dev = pendingPair
            if (dev == null) {
                LaunchedEffect(Unit) { pop() }
            } else {
                PairScreen(
                    device = dev,
                    onPair = { pwd ->
                        prefs.pairedDevice = com.dunnowsoftware.GarageAAtoESP32.data.PairedDevice(
                            address = dev.address,
                            name = dev.name,
                            password = pwd,
                        )
                        pendingPair = null
                        bust()
                        replaceAll(Route.Main)
                    },
                    onCancel = {
                        pendingPair = null
                        pop()
                    },
                )
            }
        }

        Route.Main -> MainHost(
            prefs = prefs,
            stateBust = stateBust,
            onTriggerOpen = onTriggerOpen,
            shortcutOpenPending = shortcutOpenPending,
            onSettings = { push(Route.Settings) },
        )

        Route.Settings -> {
            // Permission launchers for the geofence setup flow:
            // Step 1: fine location → Step 2: background location → Step 3: notifications → picker
            val notifLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { push(Route.GeofencePicker) }
            val bgLocationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    push(Route.GeofencePicker)
                }
            }
            val fineLocationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
                if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }

            fun requestLocationPermissions() {
                val fineOk = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val bgOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                else true
                val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                else true

                when {
                    !fineOk -> fineLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    )
                    !bgOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    !notifOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> push(Route.GeofencePicker)
                }
            }

            SettingsScreen(
                deviceName = remember(stateBust) { prefs.pairedDevice?.name },
                deviceAddress = remember(stateBust) { prefs.pairedDevice?.address },
                demoMode = remember(stateBust) { prefs.demoMode },
                currentLocaleTag = remember(stateBust) { getSavedLocaleTag(ctx) },
                presence = rememberPresence(prefs, stateBust),
                geofenceSet = remember(stateBust) { prefs.pairedDevice?.hasGeofence == true },
                geofenceEnabled = remember(stateBust) { prefs.pairedDevice?.isGeofenceActive == true },
                onBack = { pop() },
                onChangePassword = { push(Route.ChangePassword) },
                onRepair = { push(Route.Scan) },
                onUnpair = {
                    val addr = prefs.pairedDevice?.address
                    if (addr != null) GeofenceManager(ctx).unregister(addr)
                    prefs.unpairDevice()
                    bust()
                    replaceAll(Route.Scan)
                },
                onPairAnother = { push(Route.ScanAnother) },
                onToggleDemo = { v ->
                    prefs.demoMode = v
                    bust()
                },
                onLanguageScreen = { push(Route.Language) },
                onShareLog = {
                    val file = GeofenceLogger.getLogFile(ctx)
                    if (!file.exists() || file.length() == 0L) {
                        Toast.makeText(ctx, ctx.getString(R.string.toast_log_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "geofence.log")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(shareIntent, ctx.getString(R.string.toast_log_share_title)))
                    }
                },
                onGeofencePicker = { requestLocationPermissions() },
                onToggleGeofence = { enabled ->
                    prefs.setGeofenceEnabled(enabled)
                    val device = prefs.pairedDevice
                    if (device != null) {
                        val gm = GeofenceManager(ctx)
                        if (enabled) gm.register(device) else gm.unregister(device.address)
                    }
                    bust()
                },
            )
        }

        Route.GeofencePicker -> {
            val device = remember(stateBust) { prefs.pairedDevice }
            GeofencePickerScreen(
                initialLat = device?.geofenceLat,
                initialLng = device?.geofenceLng,
                initialRadiusM = device?.geofenceRadiusM,
                deviceName = device?.name,
                deviceAddress = device?.address,
                onSave = { lat, lng, radius ->
                    prefs.updateGeofence(lat, lng, radius)
                    val updated = prefs.pairedDevice
                    if (updated?.isGeofenceActive == true) {
                        GeofenceManager(ctx).register(updated)
                    }
                    bust()
                    pop()
                },
                onBack = { pop() },
            )
        }

        Route.Language -> LanguageScreen(
            currentLocaleTag = remember(stateBust) { getSavedLocaleTag(ctx) },
            onLanguageChange = { tag ->
                onApplyLocale(tag)
            },
            onBack = { pop() },
        )
    }
}

@Composable
private fun MainHost(
    prefs: DevicePreferences,
    stateBust: Int,
    onTriggerOpen: ((OpenResult) -> Unit) -> Unit,
    shortcutOpenPending: androidx.compose.runtime.MutableState<Boolean>,
    onSettings: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var openState by remember { mutableStateOf(OpenState.Idle) }
    var lastOpened by remember(stateBust) { mutableLongStateOf(prefs.lastOpenedAt) }

    LaunchedEffect(openState) {
        when (openState) {
            OpenState.Sending -> vibrate(ctx, longArrayOf(0, 30))
            OpenState.Opened  -> vibrate(ctx, longArrayOf(0, 60))
            OpenState.Failed  -> vibrate(ctx, longArrayOf(0, 40, 80, 40))
            OpenState.Idle    -> Unit
        }
        if (openState == OpenState.Opened || openState == OpenState.Failed) {
            delay(2000)
            openState = OpenState.Idle
        }
    }

    val triggerFromShortcut by shortcutOpenPending
    LaunchedEffect(triggerFromShortcut) {
        if (triggerFromShortcut && openState == OpenState.Idle && prefs.isConfigured) {
            shortcutOpenPending.value = false
            openState = OpenState.Sending
            onTriggerOpen { result ->
                when (result) {
                    is OpenResult.Success -> {
                        prefs.lastOpenedAt = System.currentTimeMillis()
                        openState = OpenState.Opened
                    }
                    is OpenResult.Failure -> openState = OpenState.Failed
                }
            }
        }
    }

    val deviceLabel = remember(stateBust) {
        val paired = prefs.pairedDevice
        when {
            prefs.demoMode -> ctx.getString(R.string.main_demo_mode)
            paired != null -> "ESP32 · ${paired.name}"
            else           -> ctx.getString(R.string.main_not_configured)
        }
    }

    val presence = rememberPresence(prefs, stateBust)

    MainScreen(
        deviceLabel = deviceLabel,
        state = openState,
        presence = presence,
        lastOpenedLabel = lastOpened.takeIf { it > 0 }?.let { formatTime(ctx, it) },
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

@Composable
private fun rememberPresence(prefs: DevicePreferences, stateBust: Int): PresenceStatus {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val demo = remember(stateBust) { prefs.demoMode }
    val address = remember(stateBust) { prefs.pairedDevice?.address }

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
        } catch (_: Throwable) { }
        onDispose { scanner.stop() }
    }

    LaunchedEffect(address) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val staleAfterMs = 15_000L
    return if (lastSeenMs > 0 && (nowMs - lastSeenMs) < staleAfterMs)
        PresenceStatus.InRange
    else
        PresenceStatus.OutOfRange
}

private fun initialStack(prefs: DevicePreferences): List<Route> = when {
    prefs.isConfigured -> listOf(Route.Main)
    else               -> listOf(Route.Welcome)
}

private fun formatTime(ctx: android.content.Context, epochMs: Long): String {
    val now = System.currentTimeMillis()
    val locale = Locale.getDefault()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.US).run {
        format(Date(now)) == format(Date(epochMs))
    }
    val timeFmt = android.text.format.DateFormat.getTimeFormat(ctx)
    return if (sameDay) {
        ctx.getString(R.string.main_today, timeFmt.format(Date(epochMs)))
    } else {
        SimpleDateFormat("MMM d, ", locale).format(Date(epochMs)) + timeFmt.format(Date(epochMs))
    }
}


private val routeStackSaver = androidx.compose.runtime.saveable.listSaver<List<Route>, String>(
    save = { it.map(::routeKey) },
    restore = { it.map(::keyRoute) },
)

private fun routeKey(r: Route): String = when (r) {
    Route.Welcome        -> "welcome"
    Route.ChangePassword -> "chg_pwd"
    Route.Scan           -> "scan"
    Route.ScanAnother    -> "scan_another"
    Route.Pair           -> "pair"
    Route.Main           -> "main"
    Route.Settings       -> "settings"
    Route.Language       -> "language"
    Route.GeofencePicker -> "geofence_picker"
}

private fun keyRoute(k: String): Route = when (k) {
    "welcome"          -> Route.Welcome
    "chg_pwd"          -> Route.ChangePassword
    "scan"             -> Route.Scan
    "scan_another"     -> Route.ScanAnother
    "pair"             -> Route.Pair
    "settings"         -> Route.Settings
    "language"         -> Route.Language
    "geofence_picker"  -> Route.GeofencePicker
    else               -> Route.Main
}

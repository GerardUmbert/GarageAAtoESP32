package com.dunnowsoftware.GarageAAtoESP32.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.dunnowsoftware.GarageAAtoESP32.ble.BleConstants
import com.dunnowsoftware.GarageAAtoESP32.transport.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.transport.activeTransport
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.data.getSavedLocaleTag
import com.dunnowsoftware.GarageAAtoESP32.data.localeListFromTag
import com.dunnowsoftware.GarageAAtoESP32.data.saveLocaleTag
import androidx.core.content.FileProvider
import com.dunnowsoftware.GarageAAtoESP32.GarageScreen
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceForegroundService
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceLogger
import com.dunnowsoftware.GarageAAtoESP32.geofence.GeofenceManager
import com.dunnowsoftware.GarageAAtoESP32.geofence.scheduleGeofenceRestore
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dunnowsoftware.GarageAAtoESP32.ui.screens.*
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageTheme
import com.dunnowsoftware.GarageAAtoESP32.wear.hasWatchPairedButNotInstalled
import com.dunnowsoftware.GarageAAtoESP32.wear.installWearCompanion
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchAutoFired
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchSending
import com.dunnowsoftware.GarageAAtoESP32.wear.syncDevicesToWatch
import com.dunnowsoftware.GarageAAtoESP32.wear.WearMessageListenerService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneActivity : AppCompatActivity() {

    private lateinit var prefs: DevicePreferences
    private var currentTransport: com.dunnowsoftware.GarageAAtoESP32.transport.OpenTransport? = null
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
        GeofenceManager(this).reregisterAll()
        scheduleGeofenceRestore(this)
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
        currentTransport?.cleanup()
    }

    private fun triggerOpen(onResult: (OpenResult) -> Unit) {
        if (prefs.demoMode) {
            android.os.Handler(mainLooper).postDelayed(
                { onResult(DemoOpener.nextResult()) },
                DemoOpener.DELAY_MS,
            )
            return
        }
        val selected = prefs.selectedDevice
        val transportType = selected?.transport
        val deviceAddress = selected?.addressKey ?: ""
        val deviceName = selected?.name ?: ""
        val transport = activeTransport(this, selected?.id)
        if (transport == null) {
            onResult(OpenResult.Failure("No paired device"))
            return
        }
        currentTransport = transport
        transport.open(trigger = TriggerSource.MANUAL_PHONE) { result ->
            val outcome = when {
                result is OpenResult.Success -> OpenOutcome.SUCCESS
                transportType == TransportType.WEBHOOK -> OpenOutcome.FAILED_WEBHOOK
                else -> OpenOutcome.FAILED_BLE
            }
            OpenHistoryStore.append(
                this,
                OpenHistoryEntry(
                    timestampMs   = System.currentTimeMillis(),
                    deviceAddress = deviceAddress,
                    deviceName    = deviceName,
                    trigger       = TriggerSource.MANUAL_PHONE,
                    outcome       = outcome,
                    deviceId      = selected?.id,
                ),
            )
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
    data object History        : Route
    data object WebhookSetup   : Route
    data class DeviceDetail(val deviceId: String) : Route
    data class GeofenceOnboarding(
        val stepIds: List<String>,
        val afterPickerNeeded: Boolean,
    ) : Route
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
    // Which device a single-purpose edit route (WebhookSetup as "Edit", ChangePassword)
    // is currently acting on. Set right before pushing that route; null when pushing
    // WebhookSetup for "Add a device" instead.
    var editingDeviceId by remember { mutableStateOf<String?>(null) }
    val current = routeStack.last()

    fun push(r: Route) { routeStack = routeStack + r }
    fun pop() {
        if (routeStack.size > 1) routeStack = routeStack.dropLast(1)
    }
    fun replaceAll(r: Route) { routeStack = listOf(r) }
    fun bust() { stateBust++ }

    // Refresh the watch's copy of the device list on every launch, covering the
    // case where devices changed while the watch was unreachable (off, out of
    // BT range) and missed the per-mutation push below.
    LaunchedEffect(Unit) { syncDevicesToWatch(ctx) }

    BackHandler(enabled = routeStack.size > 1) { pop() }

    when (current) {
        Route.Welcome -> WelcomeScreen(
            onGetStarted = { push(Route.Scan) },
            onUseWebhook = { push(Route.WebhookSetup) },
        )

        Route.WebhookSetup -> {
            // Editing an existing webhook device (reached via "Edit" from Settings/Device
            // Detail) carries its id in editingDeviceId so onSave updates it in place
            // instead of appending a new device.
            val editing = remember(stateBust) { editingDeviceId?.let { prefs.device(it) } }
            WebhookSetupScreen(
                initialName = editing?.webhook?.name.orEmpty(),
                initialUrl = editing?.webhook?.url.orEmpty(),
                initialToken = editing?.webhook?.authToken.orEmpty(),
                onSave = { name, url, token ->
                    val config = com.dunnowsoftware.GarageAAtoESP32.data.WebhookConfig(
                        url = url,
                        authToken = token,
                        name = name,
                    )
                    if (editing != null) {
                        prefs.updateDevice(editing.withWebhook(config))
                    } else {
                        prefs.addDevice(com.dunnowsoftware.GarageAAtoESP32.data.GarageDevice.webhook(config = config))
                    }
                    syncDevicesToWatch(ctx)
                    editingDeviceId = null
                    bust()
                    replaceAll(Route.Main)
                },
                onCancel = {
                    editingDeviceId = null
                    pop()
                },
            )
        }

        Route.ChangePassword -> {
            val target = remember(stateBust) { editingDeviceId?.let { prefs.device(it) } }
            SetPasswordScreen(
                initialPassword = target?.ble?.password.orEmpty(),
                onSave = { pwd ->
                    val ble = target?.ble
                    if (target != null && ble != null) {
                        prefs.updateDevice(target.withBle(ble.copy(password = pwd)))
                    }
                    editingDeviceId = null
                    bust()
                    pop()
                    Toast.makeText(ctx, ctx.getString(R.string.toast_password_updated), Toast.LENGTH_SHORT).show()
                },
                onBack = {
                    editingDeviceId = null
                    pop()
                },
                title = ctx.getString(R.string.password_screen_change_title),
                description = ctx.getString(R.string.password_screen_change_description),
                saveLabel = ctx.getString(R.string.password_screen_change_save),
            )
        }

        Route.Scan -> ScanScreen(
            onPicked = { dev ->
                pendingPair = dev
                push(Route.Pair)
            },
            onBack = if (routeStack.size > 1) ({ pop() }) else null,
            onSkip = { replaceAll(Route.Main) },
            onSelectWebhook = { push(Route.WebhookSetup) },
        )

        Route.ScanAnother -> ScanScreen(
            excludeAddresses = remember(stateBust) { prefs.devices.mapNotNull { it.ble?.address }.toSet() },
            onPicked = { dev ->
                pendingPair = dev
                push(Route.Pair)
            },
            onBack = { pop() },
            onSelectWebhook = { push(Route.WebhookSetup) },
        )

        Route.Pair -> {
            val dev = pendingPair
            if (dev == null) {
                LaunchedEffect(Unit) { pop() }
            } else {
                PairScreen(
                    device = dev,
                    onPair = { pwd ->
                        prefs.addDevice(
                            com.dunnowsoftware.GarageAAtoESP32.data.GarageDevice.ble(
                                device = com.dunnowsoftware.GarageAAtoESP32.data.PairedDevice(
                                    address = dev.address,
                                    name = dev.name,
                                    password = pwd,
                                    hasWebLog = dev.hasWebLog,
                                ),
                            )
                        )
                        syncDevicesToWatch(ctx)
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
            onHistory = { push(Route.History) },
            onSelectDevice = { deviceId ->
                prefs.selectedDeviceId = deviceId
                syncDevicesToWatch(ctx)
                bust()
            },
            onDeviceSelectionChangedExternally = { bust() },
        )

        Route.Settings -> {
            fun collectMissingSteps(geofenceAlreadySet: Boolean) = collectMissingStepsFor(ctx, geofenceAlreadySet)

            val devices = remember(stateBust) { prefs.devices.map { it.toSummary() } }
            val single = devices.singleOrNull()
            val singleDevice = remember(stateBust) { prefs.devices.singleOrNull() }
            val bleAddresses = remember(stateBust) { devices.mapNotNull { it.bleAddress }.toSet() }
            val blePresence = if (single == null) rememberMultiPresence(bleAddresses, stateBust) else emptyMap()

            fun unpairOrRemove(deviceId: String) {
                val device = prefs.device(deviceId)
                GeofenceManager(ctx).unregister(device?.addressKey ?: deviceId)
                prefs.removeDevice(deviceId)
                syncDevicesToWatch(ctx)
                bust()
                if (prefs.devices.isEmpty()) replaceAll(Route.Scan) else replaceAll(Route.Settings)
            }

            SettingsScreen(
                devices = devices,
                demoMode = remember(stateBust) { prefs.demoMode },
                currentLocaleTag = remember(stateBust) { getSavedLocaleTag(ctx) },
                presence = rememberPresence(prefs, stateBust),
                blePresence = blePresence,
                geofenceSet = remember(stateBust) { singleDevice?.hasGeofence == true },
                geofenceEnabled = remember(stateBust) { singleDevice?.isGeofenceActive == true },
                onBack = { pop() },
                onChangePassword = { deviceId ->
                    editingDeviceId = deviceId
                    push(Route.ChangePassword)
                },
                onRepair = { push(Route.Scan) },
                onUnpair = { deviceId -> unpairOrRemove(deviceId) },
                onRemoveWebhook = { deviceId -> unpairOrRemove(deviceId) },
                onEditWebhook = { deviceId ->
                    editingDeviceId = deviceId
                    push(Route.WebhookSetup)
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
                onConnectToAp = { deviceId -> connectToDeviceAp(ctx, prefs.device(deviceId)?.ble) },
                onDeviceDetail = { deviceId -> push(Route.DeviceDetail(deviceId)) },
                onGeofencePicker = {
                    if (single == null) return@SettingsScreen
                    editingDeviceId = single.id
                    val missing = collectMissingSteps(geofenceAlreadySet = false)
                    if (missing.isEmpty()) push(Route.GeofencePicker)
                    else push(Route.GeofenceOnboarding(missing.map { it.name }, afterPickerNeeded = true))
                },
                onToggleGeofence = { enabled ->
                    val device = singleDevice ?: return@SettingsScreen
                    if (!enabled) {
                        prefs.setGeofenceEnabled(device.id, false)
                        GeofenceManager(ctx).unregister(device.addressKey)
                        bust()
                    } else {
                        val geofenceSet = device.hasGeofence
                        val missing = collectMissingSteps(geofenceAlreadySet = geofenceSet)
                        if (missing.isEmpty()) {
                            prefs.setGeofenceEnabled(device.id, true)
                            val updated = prefs.device(device.id)
                            if (updated != null) GeofenceManager(ctx).register(updated)
                            bust()
                        } else {
                            editingDeviceId = device.id
                            push(Route.GeofenceOnboarding(missing.map { it.name }, afterPickerNeeded = !geofenceSet))
                        }
                    }
                },
            )
        }

        is Route.DeviceDetail -> {
            val route = current as Route.DeviceDetail
            val device = remember(stateBust) { prefs.device(route.deviceId) }
            if (device == null) {
                LaunchedEffect(Unit) { pop() }
            } else {
                DeviceDetailScreen(
                    device = device.toSummary(),
                    presence = rememberPresence(prefs, stateBust, deviceId = route.deviceId),
                    geofenceSet = device.hasGeofence,
                    geofenceEnabled = device.isGeofenceActive,
                    onBack = { pop() },
                    onChangePassword = {
                        editingDeviceId = route.deviceId
                        push(Route.ChangePassword)
                    },
                    onRepair = { push(Route.Scan) },
                    onRemove = {
                        GeofenceManager(ctx).unregister(device.addressKey)
                        prefs.removeDevice(route.deviceId)
                        syncDevicesToWatch(ctx)
                        bust()
                        pop()
                    },
                    onEditWebhook = {
                        editingDeviceId = route.deviceId
                        push(Route.WebhookSetup)
                    },
                    onConnectToAp = { connectToDeviceAp(ctx, device.ble) },
                    onGeofencePicker = {
                        editingDeviceId = route.deviceId
                        val missing = collectMissingStepsFor(ctx, geofenceAlreadySet = false)
                        if (missing.isEmpty()) push(Route.GeofencePicker)
                        else push(Route.GeofenceOnboarding(missing.map { it.name }, afterPickerNeeded = true))
                    },
                    onToggleGeofence = { enabled ->
                        if (!enabled) {
                            prefs.setGeofenceEnabled(route.deviceId, false)
                            GeofenceManager(ctx).unregister(device.addressKey)
                            bust()
                        } else {
                            val geofenceSet = device.hasGeofence
                            val missing = collectMissingStepsFor(ctx, geofenceAlreadySet = geofenceSet)
                            if (missing.isEmpty()) {
                                prefs.setGeofenceEnabled(route.deviceId, true)
                                val updated = prefs.device(route.deviceId)
                                if (updated != null) GeofenceManager(ctx).register(updated)
                                bust()
                            } else {
                                editingDeviceId = route.deviceId
                                push(Route.GeofenceOnboarding(missing.map { it.name }, afterPickerNeeded = !geofenceSet))
                            }
                        }
                    },
                )
            }
        }

        is Route.GeofenceOnboarding -> {
            val route = current as Route.GeofenceOnboarding
            val steps = buildOnboardingSteps(ctx, route.stepIds)

            // Each launcher holds a pending advance callback — called only when the check passes.
            var pendingAdvance by remember { mutableStateOf<(() -> Unit)?>(null) }

            val unusedAppLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val ok = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || ctx.packageManager.isAutoRevokeWhitelisted
                if (ok) pendingAdvance?.invoke()
                pendingAdvance = null
            }
            val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) pendingAdvance?.invoke()
                pendingAdvance = null
            }
            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) pendingAdvance?.invoke()
                pendingAdvance = null
            }
            val bgLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) pendingAdvance?.invoke()
                pendingAdvance = null
            }
            val fineLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) pendingAdvance?.invoke()
                pendingAdvance = null
            }
            val activityRecognitionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) pendingAdvance?.invoke()
                pendingAdvance = null
            }

            GeofencePermissionOnboardingScreen(
                steps = steps,
                onStepAction = { id, advance ->
                    pendingAdvance = advance
                    when (id) {
                        OnboardingStepId.FINE_LOCATION -> fineLocationLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                        )
                        OnboardingStepId.BACKGROUND_LOCATION -> bgLocationLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                        OnboardingStepId.ACTIVITY_RECOGNITION -> activityRecognitionLauncher.launch(
                            Manifest.permission.ACTIVITY_RECOGNITION
                        )
                        OnboardingStepId.NOTIFICATIONS -> notifLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                        OnboardingStepId.BATTERY -> batteryLauncher.launch(
                            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                                data = Uri.parse("package:${ctx.packageName}")
                            }.takeIf { ctx.packageManager.resolveActivity(it, 0) != null }
                                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                }
                        )
                        OnboardingStepId.UNUSED_APP -> unusedAppLauncher.launch(
                            IntentCompat.createManageUnusedAppRestrictionsIntent(ctx, ctx.packageName)
                        )
                    }
                },
                onCancel = { pop() },
                onDone = {
                    pop()
                    if (route.afterPickerNeeded) {
                        push(Route.GeofencePicker)
                    } else {
                        val deviceId = editingDeviceId
                        if (deviceId != null) {
                            prefs.setGeofenceEnabled(deviceId, true)
                            val updated = prefs.device(deviceId)
                            if (updated != null) GeofenceManager(ctx).register(updated)
                        }
                        bust()
                    }
                },
                cancelLabel = ctx.getString(
                    if (route.afterPickerNeeded) R.string.onboarding_skip else R.string.onboarding_cancel
                ),
            )
        }

        Route.GeofencePicker -> {
            // editingDeviceId is set by whichever entry point pushed this route
            // (Settings' single-device Auto-open section, or a Device Detail screen).
            val target = remember(stateBust) { editingDeviceId?.let { prefs.device(it) } }
            GeofencePickerScreen(
                initialLat = target?.geofenceLat,
                initialLng = target?.geofenceLng,
                initialRadiusM = target?.geofenceRadiusM,
                initialOuterOffsetM = target?.geofenceOuterOffsetM,
                deviceName = target?.name,
                deviceAddress = target?.addressKey,
                onSave = { lat, lng, radius, outerOffset ->
                    val id = editingDeviceId
                    if (id != null) {
                        prefs.updateGeofence(id, lat, lng, radius, outerOffset)
                        val updated = prefs.device(id)
                        if (updated?.isGeofenceActive == true) {
                            GeofenceManager(ctx).register(updated)
                        }
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

        Route.History -> HistoryScreen(
            // Unfiltered once there's more than one device — "the" device to filter
            // by is only unambiguous in the single-device case.
            deviceAddress = remember(stateBust) { prefs.devices.singleOrNull()?.addressKey },
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
    onHistory: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onDeviceSelectionChangedExternally: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var openState by remember { mutableStateOf(OpenState.Idle) }
    var lastOpened by remember(stateBust) { mutableLongStateOf(prefs.lastOpenedAt) }
    var showWearBanner by remember { mutableStateOf(false) }
    var wearBannerDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!wearBannerDismissed && hasWatchPairedButNotInstalled(ctx)) {
            showWearBanner = true
        }
    }

    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WearMessageListenerService.ACTION_WEAR_SENDING -> {
                        if (openState == OpenState.Idle) openState = OpenState.Sending
                        // A watch-side device pick (if any) is already written to prefs by
                        // the time this fires — re-read so the "Selected opener" dropdown
                        // doesn't keep showing a stale value until an unrelated bust().
                        onDeviceSelectionChangedExternally()
                    }
                    GeofenceForegroundService.ACTION_AUTO_OPENED -> {
                        if (openState != OpenState.Failed) openState = OpenState.Opened
                    }
                    GeofenceForegroundService.ACTION_AUTO_FAILED -> {
                        openState = OpenState.Failed
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WearMessageListenerService.ACTION_WEAR_SENDING)
            addAction(GeofenceForegroundService.ACTION_AUTO_OPENED)
            addAction(GeofenceForegroundService.ACTION_AUTO_FAILED)
        }
        LocalBroadcastManager.getInstance(ctx).registerReceiver(receiver, filter)
        onDispose {
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver)
        }
    }

    // Android Auto runs in the OS's separate car-app process, so an AA-triggered
    // open (manual tap, device picker, presence-based auto-open, voice) is
    // otherwise invisible to this screen — unlike the watch/geofence events
    // above, which are same-process and can ride LocalBroadcastManager. These
    // three mirror GarageScreen's own uiState transitions via a real,
    // package-scoped Context.sendBroadcast, so the phone's open animation
    // reacts to an AA open the same way it already does for watch/geofence
    // opens, and picks up an AA-side device pick as a side effect of the same
    // event instead of a separate, narrower signal.
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    GarageScreen.ACTION_AA_OPEN_SENDING -> {
                        if (openState == OpenState.Idle) openState = OpenState.Sending
                        onDeviceSelectionChangedExternally()
                    }
                    GarageScreen.ACTION_AA_OPEN_SUCCESS -> {
                        if (openState != OpenState.Failed) openState = OpenState.Opened
                    }
                    GarageScreen.ACTION_AA_OPEN_FAILURE -> {
                        openState = OpenState.Failed
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(GarageScreen.ACTION_AA_OPEN_SENDING)
            addAction(GarageScreen.ACTION_AA_OPEN_SUCCESS)
            addAction(GarageScreen.ACTION_AA_OPEN_FAILURE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, filter)
        }
        onDispose { ctx.unregisterReceiver(receiver) }
    }

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
            notifyWatchSending(ctx)
            onTriggerOpen { result ->
                when (result) {
                    is OpenResult.Success -> {
                        prefs.lastOpenedAt = System.currentTimeMillis()
                        openState = OpenState.Opened
                        notifyWatchResult(ctx, true)
                    }
                    is OpenResult.Failure -> {
                        openState = OpenState.Failed
                        notifyWatchResult(ctx, false)
                    }
                }
            }
        }
    }

    val deviceLabel = remember(stateBust) {
        val selected = prefs.selectedDevice
        when {
            prefs.demoMode    -> ctx.getString(R.string.main_demo_mode)
            selected != null  -> {
                val prefix = if (selected.transport == com.dunnowsoftware.GarageAAtoESP32.data.TransportType.WEBHOOK)
                    ctx.getString(R.string.main_webhook_prefix)
                else
                    ctx.getString(R.string.main_esp32_prefix)
                "$prefix · ${selected.name}"
            }
            else              -> ctx.getString(R.string.main_not_configured)
        }
    }
    val deviceOptions = remember(stateBust) {
        prefs.devices.map { MainDeviceOption(id = it.id, name = it.name, transport = it.transport, bleAddress = it.ble?.address) }
    }
    val dropdownBleAddresses = remember(stateBust) { deviceOptions.mapNotNull { it.bleAddress }.toSet() }
    val dropdownBlePresence =
        if (deviceOptions.size > 1) rememberMultiPresence(dropdownBleAddresses, stateBust) else emptyMap()

    val presence = rememberPresence(prefs, stateBust)

    MainScreen(
        deviceLabel = deviceLabel,
        state = openState,
        presence = presence,
        lastOpenedLabel = lastOpened.takeIf { it > 0 }?.let { formatTime(ctx, it) },
        onSettings = onSettings,
        onHistory = onHistory,
        deviceOptions = deviceOptions,
        onSelectDevice = onSelectDevice,
        blePresence = dropdownBlePresence,
        showWearBanner = showWearBanner,
        onWearInstall = {
            installWearCompanion(ctx)
            showWearBanner = false
            wearBannerDismissed = true
        },
        onWearBannerDismiss = {
            showWearBanner = false
            wearBannerDismissed = true
        },
        onOpen = {
            if (openState != OpenState.Idle) return@MainScreen
            openState = OpenState.Sending
            notifyWatchSending(ctx)
            onTriggerOpen { result ->
                when (result) {
                    is OpenResult.Success -> {
                        prefs.lastOpenedAt = System.currentTimeMillis()
                        lastOpened = prefs.lastOpenedAt
                        openState = OpenState.Opened
                        notifyWatchResult(ctx, true)
                    }
                    is OpenResult.Failure -> {
                        openState = OpenState.Failed
                        notifyWatchResult(ctx, false)
                        Toast.makeText(ctx, result.reason, Toast.LENGTH_LONG).show()
                    }
                }
            }
        },
    )
}

@Composable
private fun rememberPresence(prefs: DevicePreferences, stateBust: Int, deviceId: String? = null): PresenceStatus {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val demo = remember(stateBust) { prefs.demoMode }
    val address = remember(stateBust, deviceId) {
        val device = if (deviceId != null) prefs.device(deviceId) else prefs.selectedDevice
        device?.ble?.address
    }

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

    val staleAfterMs = 8_000L
    return if (lastSeenMs > 0 && (nowMs - lastSeenMs) < staleAfterMs)
        PresenceStatus.InRange
    else
        PresenceStatus.OutOfRange
}

/**
 * In-range status for every BLE device in [bleAddresses] at once — used by the
 * multi-device Settings list, where each BLE row needs its own presence dot.
 * Webhook devices have no BLE presence concept and simply won't appear as keys.
 */
@Composable
private fun rememberMultiPresence(bleAddresses: Set<String>, stateBust: Int): Map<String, Boolean> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lastSeenMs = remember(stateBust, bleAddresses) { mutableStateMapOf<String, Long>() }
    var nowMs by remember(bleAddresses) { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(bleAddresses) {
        val scanner = com.dunnowsoftware.GarageAAtoESP32.ble.BleScanner(ctx)
        try {
            scanner.startPresenceMulti(bleAddresses) { found ->
                lastSeenMs[found.address] = System.currentTimeMillis()
            }
        } catch (_: Throwable) { }
        onDispose { scanner.stop() }
    }

    LaunchedEffect(bleAddresses) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val staleAfterMs = 8_000L
    return bleAddresses.associateWith { address ->
        val seen = lastSeenMs[address] ?: 0L
        seen > 0 && (nowMs - seen) < staleAfterMs
    }
}

private fun initialStack(prefs: DevicePreferences): List<Route> = when {
    prefs.isConfigured -> listOf(Route.Main)
    else               -> listOf(Route.Welcome)
}

private fun com.dunnowsoftware.GarageAAtoESP32.data.GarageDevice.toSummary() = DeviceSummary(
    id = id,
    name = name,
    transport = transport,
    bleAddress = ble?.address,
    bleHasWebLog = ble?.hasWebLog == true,
    webhookUrl = webhook?.url,
)

private fun collectMissingStepsFor(ctx: Context, geofenceAlreadySet: Boolean): List<OnboardingStepId> {
    val missing = mutableListOf<OnboardingStepId>()
    if (!geofenceAlreadySet) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missing += OnboardingStepId.FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missing += OnboardingStepId.BACKGROUND_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            missing += OnboardingStepId.ACTIVITY_RECOGNITION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            missing += OnboardingStepId.NOTIFICATIONS
    }
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(ctx.packageName))
        missing += OnboardingStepId.BATTERY
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !ctx.packageManager.isAutoRevokeWhitelisted)
        missing += OnboardingStepId.UNUSED_APP
    return missing
}

private fun connectToDeviceAp(ctx: Context, paired: com.dunnowsoftware.GarageAAtoESP32.data.PairedDevice?) {
    if (paired == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val macParts = paired.address.split(":")
    val ssid = if (macParts.size == 6)
        "${paired.name}_${macParts[3]}${macParts[4]}${macParts[5]}"
    else paired.name

    val specifier = android.net.wifi.WifiNetworkSpecifier.Builder()
        .setSsid(ssid)
        .setWpa2Passphrase(paired.password)
        .setIsHiddenSsid(true)
        .build()
    val request = android.net.NetworkRequest.Builder()
        .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .setNetworkSpecifier(specifier)
        .build()
    val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
    cm.requestNetwork(request, object : android.net.ConnectivityManager.NetworkCallback() {})
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
        android.icu.text.DateTimePatternGenerator.getInstance(locale)
            .getBestPattern("MMMd")
            .let { SimpleDateFormat(it, locale).format(Date(epochMs)) } + ", " + timeFmt.format(Date(epochMs))
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
    Route.History        -> "history"
    Route.WebhookSetup   -> "webhook_setup"
    is Route.DeviceDetail -> "device_detail:${r.deviceId}"
    is Route.GeofenceOnboarding -> "geo_onboard:${r.stepIds.joinToString(",")}:${r.afterPickerNeeded}"
}

private fun keyRoute(k: String): Route = when {
    k == "welcome"          -> Route.Welcome
    k == "chg_pwd"          -> Route.ChangePassword
    k == "scan"             -> Route.Scan
    k == "scan_another"     -> Route.ScanAnother
    k == "pair"             -> Route.Pair
    k == "settings"         -> Route.Settings
    k == "language"         -> Route.Language
    k == "geofence_picker"  -> Route.GeofencePicker
    k == "history"          -> Route.History
    k == "webhook_setup"    -> Route.WebhookSetup
    k.startsWith("device_detail:") -> Route.DeviceDetail(k.removePrefix("device_detail:"))
    k.startsWith("geo_onboard:") -> {
        val payload = k.removePrefix("geo_onboard:")
        val lastColon = payload.lastIndexOf(':')
        val stepsPart = if (lastColon > 0) payload.substring(0, lastColon) else payload
        val afterPicker = payload.substringAfterLast(':') == "true"
        Route.GeofenceOnboarding(
            stepIds = stepsPart.split(",").filter { it.isNotEmpty() },
            afterPickerNeeded = afterPicker,
        )
    }
    else -> Route.Main
}

private fun buildOnboardingSteps(
    ctx: android.content.Context,
    stepIds: List<String>,
): List<OnboardingStep> = stepIds.mapNotNull { name ->
    when (runCatching { OnboardingStepId.valueOf(name) }.getOrNull()) {
        OnboardingStepId.FINE_LOCATION -> OnboardingStep(
            id = OnboardingStepId.FINE_LOCATION,
            titleRes = R.string.onboarding_location_title,
            bodyRes = R.string.onboarding_location_body,
            illustration = { LocationPermissionIllustration() },
        )
        OnboardingStepId.BACKGROUND_LOCATION -> OnboardingStep(
            id = OnboardingStepId.BACKGROUND_LOCATION,
            titleRes = R.string.onboarding_bg_location_title,
            bodyRes = R.string.onboarding_bg_location_body,
            illustration = { BackgroundLocationIllustration() },
        )
        OnboardingStepId.ACTIVITY_RECOGNITION -> OnboardingStep(
            id = OnboardingStepId.ACTIVITY_RECOGNITION,
            titleRes = R.string.onboarding_activity_title,
            bodyRes = R.string.onboarding_activity_body,
            illustration = { ActivityRecognitionIllustration() },
        )
        OnboardingStepId.NOTIFICATIONS -> OnboardingStep(
            id = OnboardingStepId.NOTIFICATIONS,
            titleRes = R.string.onboarding_notifications_title,
            bodyRes = R.string.onboarding_notifications_body,
            illustration = { NotificationsIllustration() },
        )
        OnboardingStepId.BATTERY -> OnboardingStep(
            id = OnboardingStepId.BATTERY,
            titleRes = R.string.onboarding_battery_title,
            bodyRes = R.string.onboarding_battery_body,
            illustration = { BatteryIllustration() },
        )
        OnboardingStepId.UNUSED_APP -> OnboardingStep(
            id = OnboardingStepId.UNUSED_APP,
            titleRes = R.string.onboarding_unused_title,
            bodyRes = R.string.onboarding_unused_body,
            illustration = { UnusedAppIllustration() },
        )
        null -> null
    }
}

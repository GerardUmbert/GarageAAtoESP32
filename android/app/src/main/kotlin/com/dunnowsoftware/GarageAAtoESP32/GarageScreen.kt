package com.dunnowsoftware.GarageAAtoESP32

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ble.BleScanner
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.transport.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.transport.OpenTransport
import com.dunnowsoftware.GarageAAtoESP32.transport.WebhookTransport
import com.dunnowsoftware.GarageAAtoESP32.transport.activeTransport
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchSending
import com.dunnowsoftware.GarageAAtoESP32.wear.syncDevicesToWatch

class GarageScreen(carContext: CarContext) : Screen(carContext) {

    companion object {
        // AA runs in the OS's separate car-app process, so PhoneActivity can't see
        // its state changes the way it sees same-process events (e.g. geofence
        // auto-open via LocalBroadcastManager). These use a real, package-scoped
        // Context.sendBroadcast — the same mechanism WearableListenerService relies
        // on to be invoked cross-process — so any AA-triggered open (manual tap,
        // device picker, presence-based auto-open, voice) makes the phone's own UI
        // react (open animation) exactly like a watch- or geofence-triggered open
        // already does, and picks up an AA-side device selection as a side effect
        // of the same event instead of a second, narrower signal.
        const val ACTION_AA_OPEN_SENDING = "com.dunnowsoftware.GarageAAtoESP32.ACTION_AA_OPEN_SENDING"
        const val ACTION_AA_OPEN_SUCCESS = "com.dunnowsoftware.GarageAAtoESP32.ACTION_AA_OPEN_SUCCESS"
        const val ACTION_AA_OPEN_FAILURE = "com.dunnowsoftware.GarageAAtoESP32.ACTION_AA_OPEN_FAILURE"
    }

    // The phone activity and the AA car screen run in different processes
    // (`gearhead:car` for AA), so we can't share an in-memory prefs cache
    // with the phone process. Construct a fresh DevicePreferences on every
    // read so we pick up phone-side edits (re-pair, password change, demo
    // toggle) the next time AA reads them.
    private fun prefs() = DevicePreferences(carContext)
    private var currentTransport: OpenTransport? = null
    private val presenceScanner = BleScanner(carContext)

    private enum class UiState { IDLE, CONNECTING, SUCCESS, AUTO_SUCCESS, FAILURE, AUTO_FAILURE }
    private var uiState = UiState.IDLE
    private var failureReason = ""
    private var connectAttempt = 0
    private var lastShownAutoFireAt = 0L
    private var lastShownAutoFailAt = 0L
    private var lastShownSendingAt = 0L

    // Shared debounce with GeofenceBroadcastReceiver: both read/write
    // lastAutoFiredAt in DevicePreferences so cross-process races are absorbed.
    // 10s window (design doc): enough to cover geofence latency, prevents the
    // geofence-then-BLE-in-range double-open that toggles the gate back closed.
    private val autoFireDebounceMs = 10_000L

    // Presence: matches the phone main screen. lastSeenMs=0 + inRange=false
    // means we've never heard from the paired device this session.
    private val presenceHandler = Handler(Looper.getMainLooper())
    private var lastSeenMs = 0L
    private var inRange = false
    // The MAC the running scan was started for. If prefs change (user re-pairs
    // on the phone), we'll notice the divergence on the next tick and restart
    // the scan with the new address.
    private var scanStartedForAddress: String? = null
    // 15s is generous: covers Android's LOW_POWER scan duty cycle and the
    // worst-case adv interval. Smaller windows cause the in-range/out-of-range
    // line to flicker between callbacks even when the device is stably present.
    private val presenceStaleAfterMs = 15_000L
    private val presenceCheckIntervalMs = 1500L
    private val presenceCheckRunnable = object : Runnable {
        override fun run() {
            // If the saved device address has changed since we started the
            // scan (re-pair on the phone, or a different device selected),
            // restart the scan with the new MAC.
            val currentAddress = prefs().selectedDevice?.ble?.address
            if (currentAddress != scanStartedForAddress) {
                stopPresenceScan()
                startPresenceScan()
                presenceHandler.postDelayed(this, presenceCheckIntervalMs)
                return
            }
            val nowInRange = lastSeenMs > 0 &&
                (System.currentTimeMillis() - lastSeenMs) < presenceStaleAfterMs
            if (nowInRange != inRange) {
                inRange = nowInRange
                val now = System.currentTimeMillis()
                val lastFired = prefs().lastAutoFiredAt
                val debounceOk = (now - lastFired) > autoFireDebounceMs
                if (inRange && debounceOk && uiState == UiState.IDLE && prefs().isConfigured) {
                    triggerOpen()
                } else if (uiState == UiState.IDLE) {
                    invalidate()
                }
            }
            if (uiState == UiState.IDLE) {
                val now = System.currentTimeMillis()
                val p = prefs()
                val lastSending = p.lastSendingAt
                if (lastSending > lastShownSendingAt && (now - lastSending) < 60_000L) {
                    lastShownSendingAt = lastSending
                    uiState = UiState.CONNECTING
                    invalidate()
                }
            }
            // Show result if a result arrived — works from both IDLE and CONNECTING.
            if (uiState == UiState.IDLE || uiState == UiState.CONNECTING) {
                val now = System.currentTimeMillis()
                val p = prefs()
                val lastAutoFired = p.lastAutoFiredAt
                if (lastAutoFired > lastShownAutoFireAt && (now - lastAutoFired) < 15_000L) {
                    lastShownAutoFireAt = lastAutoFired
                    uiState = UiState.AUTO_SUCCESS
                    invalidate()
                    Handler(Looper.getMainLooper()).postDelayed({
                        carContext.mainExecutor.execute { resetToIdle() }
                    }, 5_000L)
                }
                val lastAutoFailed = p.lastAutoFailedAt
                if (lastAutoFailed > lastShownAutoFailAt && (now - lastAutoFailed) < 15_000L) {
                    lastShownAutoFailAt = lastAutoFailed
                    uiState = UiState.AUTO_FAILURE
                    invalidate()
                    Handler(Looper.getMainLooper()).postDelayed({
                        carContext.mainExecutor.execute { resetToIdle() }
                    }, 5_000L)
                }
            }

            presenceHandler.postDelayed(this, presenceCheckIntervalMs)
        }
    }

    init {
        // Start/stop the presence scan tied to the screen's visibility so we
        // don't burn the BT radio when the AA UI isn't showing this screen.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = startPresenceScan()
            override fun onStop(owner: LifecycleOwner) {
                stopPresenceScan()
            }
        })
    }

    private fun startPresenceScan() {
        val p = prefs()
        val address = p.selectedDevice?.ble?.address ?: return
        if (p.demoMode) return
        try {
            presenceScanner.startPresence(address) { _ ->
                lastSeenMs = System.currentTimeMillis()
            }
            scanStartedForAddress = address
        } catch (_: Throwable) {
            // Permissions / BT off — leave inRange=false so the UI shows the
            // out-of-range hint. Don't crash.
        }
        presenceHandler.removeCallbacks(presenceCheckRunnable)
        presenceHandler.postDelayed(presenceCheckRunnable, presenceCheckIntervalMs)
    }

    private fun stopPresenceScan() {
        presenceScanner.stop()
        presenceHandler.removeCallbacks(presenceCheckRunnable)
        lastSeenMs = 0
        inRange = false
        scanStartedForAddress = null
    }

    override fun onGetTemplate(): Template {
        return when (uiState) {
            UiState.CONNECTING   -> buildLoading()
            UiState.SUCCESS      -> buildMessage(carContext.getString(R.string.aa_opened), false)
            UiState.AUTO_SUCCESS -> buildMessage(carContext.getString(R.string.aa_auto_opened), false)
            UiState.AUTO_FAILURE -> buildMessage(carContext.getString(R.string.aa_auto_failed), false)
            UiState.FAILURE      -> buildMessage(carContext.getString(R.string.aa_failed, failureReason), true)
            UiState.IDLE         -> buildMain()
        }
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private fun buildMain(): Template {
        val p = prefs()
        // 2+ real devices (not demo mode, which has nothing to pick between) get a
        // row-per-device picker instead of the single button; tapping a row both
        // opens that device and updates selectedDeviceId so every other surface
        // (phone dropdown, watch) reflects the same choice next time it's shown.
        if (!p.demoMode && p.devices.size > 1) {
            return buildDevicePicker(p)
        }
        return buildSingleDevice(p)
    }

    private fun buildDevicePicker(p: DevicePreferences): Template {
        val itemList = ItemList.Builder()
        p.devices.forEach { device ->
            val isWebhook = device.transport == TransportType.WEBHOOK
            val presenceTag = if (isWebhook) null else {
                if (device.ble?.address == scanStartedForAddress && inRange)
                    carContext.getString(R.string.aa_in_range)
                else
                    carContext.getString(R.string.aa_out_of_range)
            }
            val title = if (presenceTag != null) "$presenceTag - ${device.name}" else device.name
            itemList.addItem(
                Row.Builder()
                    .setTitle(title)
                    .addText(
                        if (isWebhook) carContext.getString(R.string.settings_webhook_badge)
                        else carContext.getString(R.string.settings_paired_badge)
                    )
                    .setOnClickListener {
                        p.selectedDeviceId = device.id
                        syncDevicesToWatch(carContext)
                        triggerOpen()
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.APP_ICON)
                    .setTitle(carContext.getString(R.string.aa_garage_door))
                    .build()
            )
            .setSingleList(itemList.build())
            .build()
    }

    private fun buildSingleDevice(p: DevicePreferences): Template {
        val configured = p.isConfigured
        val selected = p.selectedDevice
        val deviceName = when {
            p.demoMode        -> carContext.getString(R.string.aa_demo_mode)
            selected != null  -> selected.name
            else              -> carContext.getString(R.string.aa_not_configured)
        }
        // BLE presence (in range/out of range) has no equivalent for webhook
        // targets — there's no proximity signal to report (no BLE, and this
        // screen doesn't check geofence/location), so the tag is omitted
        // entirely rather than showing a permanently-wrong "out of range".
        val presenceTag = when {
            !configured           -> null
            p.demoMode            -> carContext.getString(R.string.aa_in_range)
            selected?.ble == null -> null
            inRange               -> carContext.getString(R.string.aa_in_range)
            else                  -> carContext.getString(R.string.aa_out_of_range)
        }
        // Merge presence + device name on the same body line, with the
        // presence tag in front so the dot is the first thing the eye
        // catches in a glance.
        val deviceLine = if (presenceTag != null) "$presenceTag - $deviceName" else deviceName

        val openAction = Action.Builder()
            .setTitle(carContext.getString(R.string.aa_open_garage))
            .setOnClickListener { if (configured) triggerOpen() }
            .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.aa_garage_door))
                    .addText(deviceLine)
                    .build()
            )
            .addAction(openAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())
            .build()
    }

    private fun buildLoading(): Template {
        val maxAttempts = if (prefs().selectedDevice?.transport == TransportType.WEBHOOK)
            WebhookTransport.MAX_ATTEMPTS
        else
            GarageBleManager.MAX_ATTEMPTS
        val msg = if (connectAttempt <= 1)
            carContext.getString(R.string.aa_connecting)
        else
            carContext.getString(R.string.aa_connecting_attempt, connectAttempt, maxAttempts)
        return MessageTemplate.Builder(msg)
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())
            .setLoading(true)
            .build()
    }

    private fun buildMessage(text: String, isError: Boolean): Template {
        val template = MessageTemplate.Builder(text)
            .setHeader(Header.Builder().setStartHeaderAction(Action.APP_ICON).build())

        if (isError) {
            template.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.aa_try_again))
                    .setOnClickListener { triggerOpen() }
                    .build()
            )
        }
        return template.build()
    }

    // ── Voice entry point ─────────────────────────────────────────────────────

    fun onVoiceOpen() {
        if (uiState == UiState.IDLE && prefs().isConfigured) {
            carContext.mainExecutor.execute { triggerOpen() }
        }
    }

    // ── Open trigger ──────────────────────────────────────────────────────────

    private fun triggerOpen() {
        val p = prefs()
        if (p.demoMode) {
            triggerDemo()
            return
        }
        val selected = p.selectedDevice
        val transportType = selected?.transport
        val deviceAddress = selected?.addressKey ?: ""
        val deviceName = selected?.name ?: ""
        val transport = activeTransport(carContext, selected?.id) ?: return

        uiState = UiState.CONNECTING
        invalidate()
        notifyWatchSending(carContext)
        broadcastToPhone(ACTION_AA_OPEN_SENDING)

        currentTransport = transport
        transport.open(
            trigger = TriggerSource.MANUAL_AA,
            onAttempt = { n ->
                carContext.mainExecutor.execute {
                    connectAttempt = n
                    invalidate()
                }
            }
        ) { result ->
            carContext.mainExecutor.execute {
                when (result) {
                    is OpenResult.Success -> {
                        val ts = System.currentTimeMillis()
                        prefs().lastAutoFiredAt = ts
                        OpenHistoryStore.append(
                            carContext,
                            OpenHistoryEntry(
                                timestampMs   = ts,
                                deviceAddress = deviceAddress,
                                deviceName    = deviceName,
                                trigger       = TriggerSource.MANUAL_AA,
                                outcome       = OpenOutcome.SUCCESS,
                                deviceId      = selected?.id,
                            ),
                        )
                        notifyWatchResult(carContext, true)
                        broadcastToPhone(ACTION_AA_OPEN_SUCCESS)
                        uiState = UiState.SUCCESS
                        invalidate()
                        Handler(Looper.getMainLooper()).postDelayed({
                            carContext.mainExecutor.execute { resetToIdle() }
                        }, 2000)
                    }
                    is OpenResult.Failure -> {
                        val outcome = if (transportType == TransportType.WEBHOOK) OpenOutcome.FAILED_WEBHOOK else OpenOutcome.FAILED_BLE
                        OpenHistoryStore.append(
                            carContext,
                            OpenHistoryEntry(
                                timestampMs   = System.currentTimeMillis(),
                                deviceAddress = deviceAddress,
                                deviceName    = deviceName,
                                trigger       = TriggerSource.MANUAL_AA,
                                outcome       = outcome,
                                deviceId      = selected?.id,
                            ),
                        )
                        broadcastToPhone(ACTION_AA_OPEN_FAILURE)
                        uiState = UiState.FAILURE
                        failureReason = result.reason
                        invalidate()
                    }
                }
            }
        }
    }

    private fun triggerDemo() {
        uiState = UiState.CONNECTING
        connectAttempt = 1
        invalidate()
        Handler(Looper.getMainLooper()).postDelayed({
            carContext.mainExecutor.execute {
                when (val result = DemoOpener.nextResult()) {
                    is OpenResult.Success -> {
                        uiState = UiState.SUCCESS
                        invalidate()
                        Handler(Looper.getMainLooper()).postDelayed({
                            carContext.mainExecutor.execute { resetToIdle() }
                        }, 2000)
                    }
                    is OpenResult.Failure -> {
                        uiState = UiState.FAILURE
                        failureReason = result.reason
                        invalidate()
                    }
                }
            }
        }, DemoOpener.DELAY_MS)
    }

    private fun resetToIdle() {
        uiState = UiState.IDLE
        invalidate()
    }

    private fun broadcastToPhone(action: String) {
        carContext.sendBroadcast(Intent(action).setPackage(carContext.packageName))
    }
}

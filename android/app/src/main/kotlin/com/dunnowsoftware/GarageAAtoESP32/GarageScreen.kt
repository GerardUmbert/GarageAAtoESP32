package com.dunnowsoftware.GarageAAtoESP32

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
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult

class GarageScreen(carContext: CarContext) : Screen(carContext) {

    // The phone activity and the AA car screen run in different processes
    // (`gearhead:car` for AA), so we can't share an in-memory prefs cache
    // with the phone process. Construct a fresh DevicePreferences on every
    // read so we pick up phone-side edits (re-pair, password change, demo
    // toggle) the next time AA reads them.
    private fun prefs() = DevicePreferences(carContext)
    private val bleManager = GarageBleManager(carContext)
    private val presenceScanner = BleScanner(carContext)

    private enum class UiState { IDLE, CONNECTING, SUCCESS, AUTO_SUCCESS, FAILURE, AUTO_FAILURE }
    private var uiState = UiState.IDLE
    private var failureReason = ""
    private var connectAttempt = 0
    private var lastShownAutoFireAt = 0L
    private var lastShownAutoFailAt = 0L

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
            // scan (re-pair on the phone), restart the scan with the new MAC.
            val currentAddress = prefs().pairedDevice?.address
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
            // Show "Opened automatically" or "Couldn't open automatically" if the geofence fired recently.
            if (uiState == UiState.IDLE) {
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
        val address = p.pairedDevice?.address ?: return
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
        val configured = p.isConfigured
        val paired = p.pairedDevice
        val deviceName = when {
            p.demoMode     -> carContext.getString(R.string.aa_demo_mode)
            paired != null -> paired.name
            else           -> carContext.getString(R.string.aa_not_configured)
        }
        val presenceTag = when {
            !configured -> null
            p.demoMode  -> carContext.getString(R.string.aa_in_range)
            inRange     -> carContext.getString(R.string.aa_in_range)
            else        -> carContext.getString(R.string.aa_out_of_range)
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
        val msg = if (connectAttempt <= 1)
            carContext.getString(R.string.aa_connecting)
        else
            carContext.getString(R.string.aa_connecting_attempt, connectAttempt, GarageBleManager.MAX_ATTEMPTS)
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

    // ── BLE trigger ───────────────────────────────────────────────────────────

    private fun triggerOpen() {
        val p = prefs()
        if (p.demoMode) {
            triggerDemo()
            return
        }
        val paired = p.pairedDevice ?: return

        uiState = UiState.CONNECTING
        invalidate()

        bleManager.connectAndOpen(paired.address, paired.password,
            trigger = com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource.MANUAL_AA,
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
                        val p = prefs()
                        p.lastAutoFiredAt = ts
                        p.pairedDevice?.let { dev ->
                            OpenHistoryStore.append(
                                carContext,
                                OpenHistoryEntry(
                                    timestampMs   = ts,
                                    deviceAddress = dev.address,
                                    deviceName    = dev.name,
                                    trigger       = TriggerSource.MANUAL_AA,
                                    outcome       = OpenOutcome.SUCCESS,
                                ),
                            )
                        }
                        notifyWatchResult(carContext, true)
                        uiState = UiState.SUCCESS
                        invalidate()
                        Handler(Looper.getMainLooper()).postDelayed({
                            carContext.mainExecutor.execute { resetToIdle() }
                        }, 2000)
                    }
                    is OpenResult.Failure -> {
                        prefs().pairedDevice?.let { dev ->
                            OpenHistoryStore.append(
                                carContext,
                                OpenHistoryEntry(
                                    timestampMs   = System.currentTimeMillis(),
                                    deviceAddress = dev.address,
                                    deviceName    = dev.name,
                                    trigger       = TriggerSource.MANUAL_AA,
                                    outcome       = OpenOutcome.FAILED_BLE,
                                ),
                            )
                        }
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
}

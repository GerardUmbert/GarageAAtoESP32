package com.dunnowsoftware.GarageAAtoESP32

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dunnowsoftware.GarageAAtoESP32.ble.BleScanner
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences

class GarageScreen(carContext: CarContext) : Screen(carContext) {

    // The phone activity and the AA car screen run in different processes
    // (`gearhead:car` for AA), so we can't share an in-memory prefs cache
    // with the phone process. Construct a fresh DevicePreferences on every
    // read so we pick up phone-side edits (re-pair, password change, demo
    // toggle) the next time AA reads them.
    private fun prefs() = DevicePreferences(carContext)
    private val bleManager = GarageBleManager(carContext)
    private val presenceScanner = BleScanner(carContext)

    private enum class UiState { IDLE, CONNECTING, SUCCESS, FAILURE }
    private var uiState = UiState.IDLE
    private var failureReason = ""
    private var connectAttempt = 0

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
                if (uiState == UiState.IDLE) invalidate()
            }
            presenceHandler.postDelayed(this, presenceCheckIntervalMs)
        }
    }

    init {
        // Start/stop the presence scan tied to the screen's visibility so we
        // don't burn the BT radio when the AA UI isn't showing this screen.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = startPresenceScan()
            override fun onStop(owner: LifecycleOwner)  = stopPresenceScan()
        })
    }

    private fun startPresenceScan() {
        val p = prefs()
        val address = p.pairedDevice?.address ?: return
        if (p.demoMode) return
        try {
            presenceScanner.startPresence(address) { _ ->
                lastSeenMs = System.currentTimeMillis()
                if (!inRange) {
                    inRange = true
                    if (uiState == UiState.IDLE) invalidate()
                }
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
            UiState.CONNECTING -> buildLoading()
            UiState.SUCCESS    -> buildMessage("Opened!", false)
            UiState.FAILURE    -> buildMessage("Failed: $failureReason", true)
            UiState.IDLE       -> buildMain()
        }
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private fun buildMain(): Template {
        val p = prefs()
        val configured = p.isConfigured
        val paired = p.pairedDevice
        val deviceName = when {
            p.demoMode    -> "Demo mode — no ESP32 needed"
            paired != null -> paired.name
            else          -> "Set up garage in the phone app first"
        }
        val presenceTag = when {
            !configured -> null
            p.demoMode  -> "● In range"
            inRange     -> "● In range"
            else        -> "○ Out of range"
        }
        // Merge presence + device name on the same body line, with the
        // presence tag in front so the dot is the first thing the eye
        // catches in a glance.
        val deviceLine = if (presenceTag != null) "$presenceTag - $deviceName" else deviceName

        val openAction = Action.Builder()
            .setTitle("Open Garage")
            .setOnClickListener { if (configured) triggerOpen() }
            .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Garage Door")
                    .addText(deviceLine)
                    .build()
            )
            .addAction(openAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun buildLoading(): Template {
        val msg = if (connectAttempt <= 1) "Connecting…"
                  else "Connecting… (attempt $connectAttempt / ${GarageBleManager.MAX_ATTEMPTS})"
        return MessageTemplate.Builder(msg)
            .setHeaderAction(Action.APP_ICON)
            .setLoading(true)
            .build()
    }

    private fun buildMessage(text: String, isError: Boolean): Template {
        val template = MessageTemplate.Builder(text)
            .setHeaderAction(Action.APP_ICON)

        if (isError) {
            template.addAction(
                Action.Builder()
                    .setTitle("Try Again")
                    .setOnClickListener { resetToIdle() }
                    .build()
            )
        }
        return template.build()
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

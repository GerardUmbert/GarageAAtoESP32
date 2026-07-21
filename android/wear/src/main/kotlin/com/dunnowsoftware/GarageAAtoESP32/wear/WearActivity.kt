package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val PATH_OPEN   = "/garage/open"
private const val PATH_RESULT = "/garage/result"
private const val TIMEOUT_MS  = 30_000L
private const val RESULT_DISPLAY_MS = 2_000L

class WearActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    companion object {
        const val EXTRA_AUTO_OPEN    = "auto_open"
        const val EXTRA_SHOW_RESULT  = "show_result"
        const val EXTRA_SHOW_SENDING = "show_sending"
    }

    private var openState by mutableStateOf(WatchOpenState.Idle)
    private var deviceList by mutableStateOf(WatchDeviceList(emptyList(), null))
    private var timeoutJob: Job? = null
    // Tile launches with EXTRA_AUTO_OPEN before the device list has loaded from the
    // Data Layer (async). Deferred until the first load callback lands, so the
    // 1-vs-2+-device decision below is made with a real list, not an empty guess.
    private var pendingAutoOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.also {
            it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        setContent {
            WatchMainScreen(
                state = openState,
                devices = deviceList.devices,
                selectedId = deviceList.selectedId,
                resolvedDevices = deviceList.resolvedDevices,
                onOpen = { sendOpenCommand(null) },
                onOpenDevice = { id -> sendOpenCommand(id) },
            )
        }
        loadWatchDeviceList(this) { onDeviceListLoaded(it) }
        // Only handle launch intent on a fresh launch, not on recreate.
        if (savedInstanceState == null) handleIntent(intent)
    }

    private fun onDeviceListLoaded(list: WatchDeviceList) {
        deviceList = list
        if (pendingAutoOpen) {
            pendingAutoOpen = false
            // 0/1 known devices, or 2+ with a confident geofence resolution (the
            // one-tap screen is already showing, since deviceList just updated):
            // nothing to pick between, fire immediately. 2+ with no resolution:
            // land on the picker instead of guessing which one the tile tap meant —
            // same "open the app, then pick" flow as tapping the tile with the app
            // already open.
            if (list.devices.size <= 1 || list.resolvedDevices.isNotEmpty()) sendOpenCommand(null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.getBooleanExtra(EXTRA_AUTO_OPEN, false) == true -> {
                intent.removeExtra(EXTRA_AUTO_OPEN)
                pendingAutoOpen = true
                onDeviceListLoaded(deviceList) // fires immediately if the list is already loaded (e.g. re-tap)
            }
            intent?.getBooleanExtra(EXTRA_SHOW_SENDING, false) == true -> {
                intent.removeExtra(EXTRA_SHOW_SENDING)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                if (openState == WatchOpenState.Idle) {
                    openState = WatchOpenState.Sending
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    timeoutJob?.cancel()
                    timeoutJob = lifecycleScope.launch {
                        delay(90_000L)
                        if (openState == WatchOpenState.Sending) {
                            openState = WatchOpenState.Idle
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            finish()
                        }
                    }
                }
            }
            intent?.hasExtra(EXTRA_SHOW_RESULT) == true -> {
                val success = intent.getBooleanExtra(EXTRA_SHOW_RESULT, false)
                intent.removeExtra(EXTRA_SHOW_RESULT)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                showResult(success)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getDataClient(this).addListener(this)
        loadWatchDeviceList(this) { deviceList = it }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val item = dataEvents.firstOrNull { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == "/garage/devices" }
            ?: return
        deviceList = parseWatchDeviceList(DataMapItem.fromDataItem(item.dataItem))
    }

    /** [deviceId] null means "fire whatever's selected" (single-device / legacy path); non-null is an explicit picker tap. */
    private fun sendOpenCommand(deviceId: String?) {
        if (openState != WatchOpenState.Idle) return
        openState = WatchOpenState.Sending
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@WearActivity).connectedNodes.await()
                if (nodes.isEmpty()) {
                    showResult(success = false)
                    return@launch
                }
                val payload = (deviceId ?: "").toByteArray()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@WearActivity)
                        .sendMessage(node.id, PATH_OPEN, payload)
                        .await()
                }
                timeoutJob = launch {
                    delay(TIMEOUT_MS)
                    if (openState == WatchOpenState.Sending) showResult(success = false)
                }
            } catch (_: Exception) {
                showResult(success = false)
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_RESULT) return
        timeoutJob?.cancel()
        val success = String(event.data) == "SUCCESS"
        showResult(success)
    }

    private fun showResult(success: Boolean) {
        openState = if (success) WatchOpenState.Opened else WatchOpenState.Failed
        haptic(success)
        timeoutJob = lifecycleScope.launch {
            delay(RESULT_DISPLAY_MS)
            openState = WatchOpenState.Idle
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Suppress("DEPRECATION")
    private fun haptic(success: Boolean) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (success) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80, 80, 80), -1))
        }
    }
}

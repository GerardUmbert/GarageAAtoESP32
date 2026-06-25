package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val PATH_OPEN   = "/garage/open"
private const val PATH_RESULT = "/garage/result"
private const val TIMEOUT_MS  = 10_000L
private const val RESULT_DISPLAY_MS = 2_000L

class WearActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    companion object {
        const val EXTRA_AUTO_OPEN = "auto_open"
    }

    private var openState by mutableStateOf(WatchOpenState.Idle)
    private var timeoutJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WatchMainScreen(
                state = openState,
                onOpen = ::sendOpenCommand,
            )
        }
        maybeAutoOpen(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeAutoOpen(intent)
    }

    /** Launched from the tile with EXTRA_AUTO_OPEN -> fire the open immediately. */
    private fun maybeAutoOpen(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_OPEN, false) == true) {
            // Consume the flag so a later onResume/recreate doesn't re-fire.
            intent.removeExtra(EXTRA_AUTO_OPEN)
            sendOpenCommand()
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    private fun sendOpenCommand() {
        if (openState != WatchOpenState.Idle) return
        openState = WatchOpenState.Sending

        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@WearActivity).connectedNodes.await()
                if (nodes.isEmpty()) {
                    showResult(success = false)
                    return@launch
                }
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@WearActivity)
                        .sendMessage(node.id, PATH_OPEN, ByteArray(0))
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

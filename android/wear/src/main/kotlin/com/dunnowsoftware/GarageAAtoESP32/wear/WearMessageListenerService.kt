package com.dunnowsoftware.GarageAAtoESP32.wear

import com.dunnowsoftware.GarageAAtoESP32.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

private const val PATH_RESULT     = "/garage/result"
private const val PATH_AUTOFIRED  = "/garage/autofired"
private const val CHANNEL_ID      = "garage_auto"
private const val NOTIF_ID        = 2001
private const val TILE_RESET_MS   = 3_000L

class WearMessageListenerService : WearableListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_RESULT -> {
                val success = String(event.data) == "SUCCESS"
                TileStateStore.setResult(this, success)
                GarageOpenerTileService.requestTileUpdate(this)
                haptic(success)
                // Reset tile back to Idle after linger period
                handler.postDelayed({
                    TileStateStore.set(this, TileState.Idle)
                    GarageOpenerTileService.requestTileUpdate(this)
                }, TILE_RESET_MS)
            }
            PATH_AUTOFIRED -> {
                // Geofence auto-open on the phone — show a passive notification
                postAutoOpenNotification()
            }
        }
    }

    private fun postAutoOpenNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Garage auto-open", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Opened automatically")
            .setAutoCancel(true)
            .setTimeoutAfter(5_000)
            .build()
        nm.notify(NOTIF_ID, notif)
        haptic(success = true)
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

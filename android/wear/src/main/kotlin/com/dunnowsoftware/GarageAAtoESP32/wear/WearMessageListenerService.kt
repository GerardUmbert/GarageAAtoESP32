package com.dunnowsoftware.GarageAAtoESP32.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.dunnowsoftware.GarageAAtoESP32.R
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

private const val PATH_AUTOFIRED  = "/garage/autofired"
private const val CHANNEL_ID      = "garage_auto"
private const val NOTIF_ID        = 2001

/**
 * Watch-side background listener. Open results (/garage/result) are handled directly by
 * WearActivity, which is always foreground when an open is triggered (the tile launches it).
 * This service only needs to surface the passive geofence auto-open notification.
 */
class WearMessageListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
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
        haptic()
    }

    @Suppress("DEPRECATION")
    private fun haptic() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }
}

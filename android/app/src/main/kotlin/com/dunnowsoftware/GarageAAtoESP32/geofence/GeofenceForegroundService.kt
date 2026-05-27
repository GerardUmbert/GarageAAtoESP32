package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ble.GarageBleManager
import com.dunnowsoftware.GarageAAtoESP32.ble.OpenResult
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences

private const val TAG = "GeofenceService"
private const val CHANNEL_ID = "geofence_auto_open"
private const val NOTIF_ID = 1001

// Elevated retry budget: geofence fires up to 40 m out; the phone keeps
// retrying as it drives closer, so BLE range at trigger time doesn't matter.
private const val GEOFENCE_MAX_ATTEMPTS = 8

class GeofenceForegroundService : Service() {

    private lateinit var bleManager: GarageBleManager

    override fun onCreate() {
        super.onCreate()
        bleManager = GarageBleManager(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (deviceAddress == null) {
            GeofenceLogger.w(this, TAG, "onStartCommand: no device address in intent — stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        GeofenceLogger.i(this, TAG, "Service started for $deviceAddress — budget $GEOFENCE_MAX_ATTEMPTS attempts")
        startForeground(NOTIF_ID, buildOngoingNotification())
        fireOpen(deviceAddress, startId)
        return START_NOT_STICKY
    }

    private fun fireOpen(deviceAddress: String, startId: Int) {
        val prefs = DevicePreferences(this)
        val device = prefs.pairedDevice
        if (device == null || device.address != deviceAddress) {
            GeofenceLogger.w(this, TAG, "Device $deviceAddress not found in prefs — aborting")
            stopSelf(startId)
            return
        }

        attemptSession(device.address, device.password, attemptsLeft = GEOFENCE_MAX_ATTEMPTS, startId)
    }

    private fun attemptSession(address: String, password: String, attemptsLeft: Int, startId: Int) {
        if (attemptsLeft <= 0) {
            GeofenceLogger.w(this, TAG, "All $GEOFENCE_MAX_ATTEMPTS attempts exhausted for $address — giving up")
            stopForeground(STOP_FOREGROUND_REMOVE)
            postResultNotification(success = false)
            stopSelf(startId)
            return
        }
        val sessionAttempts = minOf(attemptsLeft, GarageBleManager.MAX_ATTEMPTS)
        GeofenceLogger.d(this, TAG, "BLE session start — ${attemptsLeft} attempts remaining (this session: up to $sessionAttempts)")
        var sessionAttemptCount = 0

        bleManager.cleanup()
        bleManager.connectAndOpen(
            deviceAddress = address,
            userPin = password,
            onAttempt = { n ->
                sessionAttemptCount = n
                GeofenceLogger.d(this, TAG, "BLE attempt $n (session), ${attemptsLeft - n + 1} total remaining after this")
            },
        ) { result ->
            when (result) {
                is OpenResult.Success -> {
                    val ts = System.currentTimeMillis()
                    GeofenceLogger.i(this, TAG, "BLE open SUCCESS for $address — writing lastAutoFiredAt=$ts")
                    DevicePreferences(this).lastAutoFiredAt = ts
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    postResultNotification(success = true)
                    stopSelf(startId)
                }
                is OpenResult.Failure -> {
                    if (result.isAuthFailure) {
                        GeofenceLogger.w(this, TAG, "BLE open AUTH FAILURE for $address — wrong password, not retrying")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        postResultNotification(success = false)
                        stopSelf(startId)
                    } else {
                        val remaining = attemptsLeft - sessionAttemptCount
                        GeofenceLogger.d(this, TAG, "BLE session failed (${result.reason}) — ${remaining} attempts left, chaining next session")
                        attemptSession(address, password, remaining, startId)
                    }
                }
            }
        }
    }

    private fun buildOngoingNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.notif_geofence_opening_title))
            .setContentText(getString(R.string.notif_geofence_opening_body))
            .setOngoing(true)
            .build()
    }

    private fun postResultNotification(success: Boolean) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val (title, body) = if (success) {
            getString(R.string.notif_geofence_success_title) to
                getString(R.string.notif_geofence_success_body)
        } else {
            getString(R.string.notif_geofence_fail_title) to
                getString(R.string.notif_geofence_fail_body)
        }
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(body)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setTimeoutAfter(5_000)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_geofence_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notif_channel_geofence_desc)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bleManager.cleanup()
        super.onDestroy()
    }
}

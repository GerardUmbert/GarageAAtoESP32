package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchAutoFired
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchResult
import com.dunnowsoftware.GarageAAtoESP32.wear.notifyWatchSending
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
import com.dunnowsoftware.GarageAAtoESP32.geofence.EXTRA_DEVICE_ADDRESS

private const val TAG = "GeofenceService"
private const val CHANNEL_ID = "geofence_auto_open"
private const val NOTIF_ID = 1001

// Elevated retry budget: geofence fires up to 40 m out; the phone keeps
// retrying as it drives closer, so BLE range at trigger time doesn't matter.
private const val GEOFENCE_MAX_ATTEMPTS = 8

class GeofenceForegroundService : Service() {

    companion object {
        const val ACTION_STOP = "com.dunnowsoftware.GarageAAtoESP32.geofence.ACTION_STOP_GEOFENCE_SERVICE"
        const val ACTION_AUTO_OPENED = "com.dunnowsoftware.GarageAAtoESP32.ACTION_AUTO_OPENED"
        const val ACTION_AUTO_FAILED = "com.dunnowsoftware.GarageAAtoESP32.ACTION_AUTO_FAILED"
        const val EXTRA_GATE_DETAIL = "gate_detail"
    }

    @Volatile private var cancelled = false
    private var currentTransport: OpenTransport? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            GeofenceLogger.i(this, TAG, "Stop requested via EXIT — aborting open attempts")
            cancelled = true
            currentTransport?.cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (deviceAddress == null) {
            GeofenceLogger.w(this, TAG, "onStartCommand: no device address in intent — stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val gateDetail = intent.getStringExtra(EXTRA_GATE_DETAIL)
        GeofenceLogger.i(this, TAG, "Service started for $deviceAddress — budget $GEOFENCE_MAX_ATTEMPTS attempts")
        startForeground(NOTIF_ID, buildOngoingNotification())
        notifyWatchSending(this)
        fireOpen(deviceAddress, gateDetail, startId)
        return START_NOT_STICKY
    }

    private fun fireOpen(deviceAddress: String, gateDetail: String?, startId: Int) {
        val prefs = DevicePreferences(this)
        val device = prefs.devices.firstOrNull { it.addressKey == deviceAddress }
        if (device == null) {
            GeofenceLogger.w(this, TAG, "Device $deviceAddress not found in prefs — aborting")
            stopSelf(startId)
            return
        }

        attemptSession(deviceAddress, device.id, device.name, device.transport, gateDetail, attemptsLeft = GEOFENCE_MAX_ATTEMPTS, startId)
    }

    private fun attemptSession(address: String, deviceId: String, deviceName: String, transportType: TransportType?, gateDetail: String?, attemptsLeft: Int, startId: Int) {
        if (attemptsLeft <= 0) {
            GeofenceLogger.w(this, TAG, "All $GEOFENCE_MAX_ATTEMPTS attempts exhausted for $address — giving up")
            DevicePreferences(this).lastAutoFailedAt = System.currentTimeMillis()
            OpenHistoryStore.append(
                this,
                OpenHistoryEntry(
                    timestampMs   = System.currentTimeMillis(),
                    deviceAddress = address,
                    deviceName    = deviceName,
                    trigger       = TriggerSource.AUTO_GEOFENCE,
                    outcome       = if (transportType == TransportType.WEBHOOK) OpenOutcome.FAILED_WEBHOOK else OpenOutcome.FAILED_BLE,
                    detail        = gateDetail,
                    deviceId      = deviceId,
                ),
            )
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(ACTION_AUTO_FAILED))
            notifyWatchResult(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            postResultNotification(success = false)
            stopSelf(startId)
            return
        }
        val transport = activeTransport(this, deviceId)
        if (transport == null) {
            GeofenceLogger.w(this, TAG, "No active transport for $address — aborting")
            stopSelf(startId)
            return
        }
        val sessionMaxAttempts = if (transportType == TransportType.WEBHOOK)
            WebhookTransport.MAX_ATTEMPTS
        else
            GarageBleManager.MAX_ATTEMPTS
        val sessionAttempts = minOf(attemptsLeft, sessionMaxAttempts)
        GeofenceLogger.d(this, TAG, "Open session start — ${attemptsLeft} attempts remaining (this session: up to $sessionAttempts)")
        var sessionAttemptCount = 0

        currentTransport?.cleanup()
        currentTransport = transport
        transport.open(
            trigger = TriggerSource.AUTO_GEOFENCE,
            onAttempt = { n ->
                sessionAttemptCount = n
                GeofenceLogger.d(this, TAG, "Open attempt $n (session), ${attemptsLeft - n + 1} total remaining after this")
            },
        ) { result ->
            when (result) {
                is OpenResult.Success -> {
                    val ts = System.currentTimeMillis()
                    GeofenceLogger.i(this, TAG, "Open SUCCESS for $address — writing lastAutoFiredAt=$ts")
                    DevicePreferences(this).lastAutoFiredAt = ts
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(Intent(ACTION_AUTO_OPENED))
                    notifyWatchAutoFired()
                    notifyWatchResult(this, true)
                    OpenHistoryStore.append(
                        this,
                        OpenHistoryEntry(
                            timestampMs   = ts,
                            deviceAddress = address,
                            deviceName    = deviceName,
                            trigger       = TriggerSource.AUTO_GEOFENCE,
                            outcome       = OpenOutcome.SUCCESS,
                            detail        = gateDetail,
                            deviceId      = deviceId,
                        ),
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    postResultNotification(success = true)
                    stopSelf(startId)
                }
                is OpenResult.Failure -> {
                    if (result.isAuthFailure) {
                        GeofenceLogger.w(this, TAG, "Open AUTH FAILURE for $address — not retrying")
                        DevicePreferences(this).lastAutoFailedAt = System.currentTimeMillis()
                        OpenHistoryStore.append(
                            this,
                            OpenHistoryEntry(
                                timestampMs   = System.currentTimeMillis(),
                                deviceAddress = address,
                                deviceName    = deviceName,
                                trigger       = TriggerSource.AUTO_GEOFENCE,
                                outcome       = if (transportType == TransportType.WEBHOOK) OpenOutcome.FAILED_WEBHOOK else OpenOutcome.FAILED_BLE,
                                detail        = "AUTH_FAILURE",
                                deviceId      = deviceId,
                            ),
                        )
                        LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(Intent(ACTION_AUTO_FAILED))
                        notifyWatchResult(this, false)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        postResultNotification(success = false)
                        stopSelf(startId)
                    } else if (cancelled) {
                        GeofenceLogger.i(this, TAG, "Open session aborted — EXIT received while retrying")
                        stopSelf(startId)
                    } else {
                        val remaining = attemptsLeft - sessionAttemptCount
                        GeofenceLogger.d(this, TAG, "Open session failed (${result.reason}) — ${remaining} attempts left, chaining next session")
                        attemptSession(address, deviceId, deviceName, transportType, gateDetail, remaining, startId)
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

    private fun notifyWatchAutoFired() = notifyWatchAutoFired(this)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        currentTransport?.cleanup()
        super.onDestroy()
    }
}

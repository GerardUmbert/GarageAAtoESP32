package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.dunnowsoftware.GarageAAtoESP32.R
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

private const val TAG = "GpsWarmupService"
private const val CHANNEL_ID = "geofence_auto_open"
private const val NOTIF_ID = 1002
private const val TIMEOUT_MS = 5 * 60 * 1000L  // 5 minutes
private const val INTERVAL_MS = 5_000L

// Don't consider stopping early until the warmup has had time for at least one fresh activity update.
private const val EARLY_STOP_GRACE_MS = 30_000L
private const val EARLY_STOP_MIN_CONFIDENCE = 75
private const val EARLY_STOP_MAX_ACTIVITY_AGE_MS = 30_000L

class GpsWarmupForegroundService : Service() {

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var startedAt = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            if (loc.hasSpeed()) {
                val speedKmh = loc.speed * 3.6f
                GeofenceLogger.d(this@GpsWarmupForegroundService, TAG,
                    "GPS warmup fix — speed=%.1f km/h acc=%.1fm".format(speedKmh, loc.accuracy))
            } else {
                GeofenceLogger.d(this@GpsWarmupForegroundService, TAG,
                    "GPS warmup fix — no speed yet acc=%.1fm".format(loc.accuracy))
            }
            checkEarlyStop()
        }
    }

    private fun checkEarlyStop() {
        if (System.currentTimeMillis() - startedAt < EARLY_STOP_GRACE_MS) return

        val activityType = ActivityUpdateReceiver.lastActivityType(this)
        val confidence = ActivityUpdateReceiver.lastActivityConfidence(this)
        val ageMs = ActivityUpdateReceiver.lastActivityAgeMs(this)
        // STILL is deliberately excluded — an idling car at a red light or stop sign is
        // indistinguishable from a parked one to the motion sensors, so STILL is not a safe
        // "definitely not driving" signal. ON_FOOT/WALKING/RUNNING involve gait motion a car can't produce.
        val activityName = when (activityType) {
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            else -> null
        }
        if (activityName != null && confidence >= EARLY_STOP_MIN_CONFIDENCE && ageMs in 0..EARLY_STOP_MAX_ACTIVITY_AGE_MS) {
            GeofenceLogger.i(this, TAG, "GPS warmup early stop — activity=$activityName confidence=$confidence% (${ageMs / 1000}s ago)")
            stopSelf()
        }
    }

    private val timeoutRunnable = Runnable {
        GeofenceLogger.w(this, TAG, "GPS warmup timeout — stopping location updates")
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                GeofenceLogger.i(this, TAG, "Stop requested — removing location updates")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        GeofenceLogger.i(this, TAG, "Starting GPS warmup — ${INTERVAL_MS}ms interval, ${TIMEOUT_MS / 1000}s timeout")
        startedAt = System.currentTimeMillis()
        startForeground(NOTIF_ID, buildNotification())

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            GeofenceLogger.w(this, TAG, "Missing location permission — stopping warmup: $e")
            stopSelf()
            return START_NOT_STICKY
        }

        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        fusedClient.removeLocationUpdates(locationCallback)
        GeofenceLogger.i(this, TAG, "GPS warmup stopped — location updates removed")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            // Channel is created by GeofenceForegroundService; if somehow missing, bail gracefully
            GeofenceLogger.w(this, TAG, "Notification channel $CHANNEL_ID not found")
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.notif_gps_warmup_title))
            .setContentText(getString(R.string.notif_gps_warmup_body))
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.dunnowsoftware.GarageAAtoESP32.GPS_WARMUP_STOP"
    }
}

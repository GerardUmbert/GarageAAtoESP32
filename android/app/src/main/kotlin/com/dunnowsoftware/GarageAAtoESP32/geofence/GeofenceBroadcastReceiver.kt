package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.Geofence

private const val TAG = "GeofenceReceiver"

// Debounce: don't re-trigger if a successful auto-open happened within this window.
private const val DEBOUNCE_MS = 10_000L

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: run {
            GeofenceLogger.w(context, TAG, "onReceive: null GeofencingEvent — ignoring")
            return
        }
        if (event.hasError()) {
            GeofenceLogger.w(context, TAG, "GeofencingEvent error code: ${event.errorCode}")
            return
        }

        val transitionName = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT  -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> "UNKNOWN(${event.geofenceTransition})"
        }
        val ids = event.triggeringGeofences?.map { it.requestId } ?: emptyList()
        val loc = event.triggeringLocation
        val locStr = if (loc != null)
            "lat=%.6f lng=%.6f acc=%.1fm speed=%.1fm/s".format(loc.latitude, loc.longitude, loc.accuracy, loc.speed)
        else "no location"
        GeofenceLogger.i(context, TAG, "Geofence transition: $transitionName — IDs: $ids — $locStr")

        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            val triggeringGeofences = event.triggeringGeofences ?: return
            for (geofence in triggeringGeofences) {
                val address = geofence.requestId.removePrefix(GEOFENCE_ID_PREFIX)
                logExitContext(context, address, event)
            }
            return
        }

        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggeringGeofences = event.triggeringGeofences ?: return

        for (geofence in triggeringGeofences) {
            val address = geofence.requestId.removePrefix(GEOFENCE_ID_PREFIX)
            handleEnter(context, address, event)
        }
    }

    private fun handleEnter(context: Context, deviceAddress: String, event: GeofencingEvent) {
        val prefs = DevicePreferences(context)
        val device = prefs.pairedDevice

        if (device == null || device.address != deviceAddress) {
            GeofenceLogger.w(context, TAG, "ENTER for $deviceAddress — not the paired device (paired=${device?.address}), dropping")
            return
        }
        if (!device.isGeofenceActive) {
            GeofenceLogger.d(context, TAG, "ENTER for $deviceAddress — geofence inactive (hasGeofence=${device.hasGeofence} enabled=${device.geofenceEnabled}), dropping")
            return
        }

        val now = System.currentTimeMillis()
        val enterSpeed = event.triggeringLocation?.speed ?: -1f
        val enterLastFiredAgoMs = now - prefs.lastAutoFiredAt
        val aaConnected = com.dunnowsoftware.GarageAAtoESP32.AndroidAutoState.isConnected
        GeofenceLogger.i(context, TAG, "ENTER — device=$deviceAddress speed=${if (enterSpeed >= 0) "${enterSpeed}m/s" else "unknown"} AA connected=$aaConnected lastAutoFire ${enterLastFiredAgoMs}ms ago")

        // Gate 1 — Android Auto must be connected.
        GeofenceLogger.d(context, TAG, "Gate 1 — AA connected: ${if (aaConnected) "PASS" else "FAIL"}")
        if (!aaConnected) {
            GeofenceLogger.i(context, TAG, "ENTER suppressed: AA not connected")
            return
        }

        fireIfNotDebounced(context, deviceAddress)
    }

    private fun logExitContext(context: Context, deviceAddress: String, event: GeofencingEvent) {
        val prefs = DevicePreferences(context)
        val now = System.currentTimeMillis()
        val loc = event.triggeringLocation
        val speed = loc?.speed ?: -1f
        val aaConnected = com.dunnowsoftware.GarageAAtoESP32.AndroidAutoState.isConnected
        val lastFiredAgoMs = now - prefs.lastAutoFiredAt
        GeofenceLogger.i(context, TAG, "EXIT — device=$deviceAddress speed=${if (speed >= 0) "${speed}m/s" else "unknown"} AA connected=$aaConnected lastAutoFire ${lastFiredAgoMs}ms ago")
    }

    private fun fireIfNotDebounced(context: Context, deviceAddress: String) {
        val prefs = DevicePreferences(context)
        val now = System.currentTimeMillis()

        val lastFired = prefs.lastAutoFiredAt
        val firedAgoMs = now - lastFired
        GeofenceLogger.d(context, TAG, "Gate 3 — last successful auto-fire ${firedAgoMs}ms ago (limit=${DEBOUNCE_MS}ms): ${if (firedAgoMs >= DEBOUNCE_MS) "PASS" else "FAIL"}")
        if (firedAgoMs < DEBOUNCE_MS) {
            GeofenceLogger.i(context, TAG, "ENTER suppressed: debounce — fired ${firedAgoMs}ms ago")
            return
        }

        GeofenceLogger.i(context, TAG, "All gates passed for $deviceAddress — starting foreground service")
        val serviceIntent = Intent(context, GeofenceForegroundService::class.java).apply {
            putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
        }
        context.startForegroundService(serviceIntent)
    }
}

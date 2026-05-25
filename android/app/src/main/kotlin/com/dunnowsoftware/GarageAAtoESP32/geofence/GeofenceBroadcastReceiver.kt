package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.LocationServices

private const val TAG = "GeofenceReceiver"

// AA connection grace window: cover brief BT/USB flickers and slow AA startup.
private const val CAR_CONNECTION_GRACE_MS = 60_000L

// Debounce: don't re-trigger if a successful auto-open happened within this window.
private const val DEBOUNCE_MS = 10_000L

// Speed gate: reject walking/parked. ~11 km/h in m/s.
private const val MIN_SPEED_MS = 3.0f

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

        // Gate 1 — Android Auto connected or was connected within grace window.
        val now = System.currentTimeMillis()
        val lastCar = prefs.lastCarConnectionAt
        val carAgoMs = now - lastCar
        val carRecent = carAgoMs <= CAR_CONNECTION_GRACE_MS
        GeofenceLogger.d(context, TAG, "Gate 1 — AA last seen ${carAgoMs}ms ago (limit=${CAR_CONNECTION_GRACE_MS}ms): ${if (carRecent) "PASS" else "FAIL"}")
        if (!carRecent) {
            GeofenceLogger.i(context, TAG, "ENTER suppressed: AA not connected recently enough")
            return
        }

        // Gate 2 — Speed > 3 m/s. Use the location baked into the geofence event if available,
        // otherwise fall back to FusedLocationProviderClient.lastLocation.
        val triggerLocation = event.triggeringLocation
        val speed = triggerLocation?.speed ?: -1f
        GeofenceLogger.d(context, TAG, "Gate 2 — event location speed: ${if (speed >= 0) "${speed} m/s" else "unknown (no speed in event location)"}")
        if (speed >= 0 && speed < MIN_SPEED_MS) {
            GeofenceLogger.i(context, TAG, "ENTER suppressed: speed too low (${speed} m/s < ${MIN_SPEED_MS} m/s)")
            return
        }
        if (speed < 0) {
            GeofenceLogger.d(context, TAG, "Gate 2 — falling back to FusedLocation.lastLocation for speed")
            try {
                LocationServices.getFusedLocationProviderClient(context)
                    .lastLocation
                    .addOnSuccessListener { loc ->
                        val fallbackSpeed = loc?.speed ?: -1f
                        GeofenceLogger.d(context, TAG, "Gate 2 fallback — lastLocation speed: ${if (fallbackSpeed >= 0) "${fallbackSpeed} m/s" else "null/unknown"}")
                        if (loc == null || fallbackSpeed < MIN_SPEED_MS) {
                            GeofenceLogger.i(context, TAG, "ENTER suppressed: fallback speed too low (${fallbackSpeed} m/s)")
                            return@addOnSuccessListener
                        }
                        fireIfNotDebounced(context, deviceAddress)
                    }
                    .addOnFailureListener {
                        GeofenceLogger.w(context, TAG, "Gate 2 fallback — could not read lastLocation: $it")
                    }
            } catch (_: SecurityException) {
                GeofenceLogger.w(context, TAG, "Gate 2 fallback — no location permission")
            }
            return
        }

        fireIfNotDebounced(context, deviceAddress)
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

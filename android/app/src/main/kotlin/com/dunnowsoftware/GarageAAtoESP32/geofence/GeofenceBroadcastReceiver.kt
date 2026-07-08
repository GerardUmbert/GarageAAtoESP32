package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "GeofenceReceiver"

// Debounce: don't re-trigger if a successful auto-open happened within this window.
private const val DEBOUNCE_MS = 10_000L

// Minimum speed (km/h) treated as "driving towards the garage" for the fallback gates.
private const val MIN_TRIGGER_SPEED_KMH = 12f

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

        val triggeringGeofences = event.triggeringGeofences ?: return

        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            for (geofence in triggeringGeofences) {
                when {
                    geofence.requestId.startsWith(GEOFENCE_OUTER_ID_PREFIX) -> {
                        val address = geofence.requestId.removePrefix(GEOFENCE_OUTER_ID_PREFIX)
                        GeofenceLogger.i(context, TAG, "Outer geofence EXIT — stopping GPS warmup for $address")
                        stopGpsWarmup(context)
                    }
                    geofence.requestId.startsWith(GEOFENCE_ID_PREFIX) -> {
                        val address = geofence.requestId.removePrefix(GEOFENCE_ID_PREFIX)
                        GeofenceLogger.i(context, TAG, "Inner geofence EXIT — setting outside flag and stopping GPS warmup for $address")
                        DevicePreferences(context).wasOutsideOuterGeofence = true
                        stopGpsWarmup(context)
                        stopGeofenceService(context)
                        logExitContext(context, address, event)
                    }
                }
            }
            return
        }

        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        for (geofence in triggeringGeofences) {
            when {
                geofence.requestId.startsWith(GEOFENCE_OUTER_ID_PREFIX) -> {
                    val address = geofence.requestId.removePrefix(GEOFENCE_OUTER_ID_PREFIX)
                    handleOuterEnter(context, address)
                }
                geofence.requestId.startsWith(GEOFENCE_ID_PREFIX) -> {
                    val address = geofence.requestId.removePrefix(GEOFENCE_ID_PREFIX)
                    handleEnter(context, address, event)
                }
            }
        }
    }

    private fun handleOuterEnter(context: Context, deviceAddress: String) {
        if (com.dunnowsoftware.GarageAAtoESP32.AndroidAutoState.isConnected) {
            GeofenceLogger.i(context, TAG, "Outer geofence ENTER for $deviceAddress — AA connected, GPS already warm, skipping warmup")
            return
        }
        val activityType = ActivityUpdateReceiver.lastActivityType(context)
        val confidence = ActivityUpdateReceiver.lastActivityConfidence(context)
        val activityAgeMs = ActivityUpdateReceiver.lastActivityAgeMs(context)
        val activityAgeStr = if (activityAgeMs >= 0) "${activityAgeMs / 1000}s ago" else "never"
        val activityName = when (activityType) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT    -> "ON_FOOT"
            DetectedActivity.STILL      -> "STILL"
            DetectedActivity.RUNNING    -> "RUNNING"
            DetectedActivity.WALKING    -> "WALKING"
            else -> "UNKNOWN"
        }
        if (activityType == DetectedActivity.STILL || activityType == DetectedActivity.ON_FOOT || activityType == DetectedActivity.WALKING || activityType == DetectedActivity.RUNNING) {
            GeofenceLogger.i(context, TAG, "Outer geofence ENTER for $deviceAddress — activity=$activityName($confidence%) $activityAgeStr, skipping warmup")
            return
        }

        val wasOutside = DevicePreferences(context).wasOutsideOuterGeofence
        val isConfidentVehicle = activityType == DetectedActivity.IN_VEHICLE || activityType == DetectedActivity.ON_BICYCLE
        if (!wasOutside && !isConfidentVehicle) {
            GeofenceLogger.i(context, TAG, "Outer geofence ENTER for $deviceAddress — activity=$activityName($confidence%) $activityAgeStr, no prior EXIT recorded, skipping warmup")
            return
        }

        GeofenceLogger.i(context, TAG, "Outer geofence ENTER for $deviceAddress — activity=$activityName($confidence%) $activityAgeStr wasOutside=$wasOutside, starting GPS warmup")
        DevicePreferences(context).wasOutsideOuterGeofence = false
        val intent = Intent(context, GpsWarmupForegroundService::class.java)
        context.startForegroundService(intent)
    }

    private fun stopGpsWarmup(context: Context) {
        val intent = Intent(context, GpsWarmupForegroundService::class.java).apply {
            action = GpsWarmupForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun stopGeofenceService(context: Context) {
        val intent = Intent(context, GeofenceForegroundService::class.java).apply {
            action = GeofenceForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun handleEnter(context: Context, deviceAddress: String, event: GeofencingEvent) {
        val prefs = DevicePreferences(context)
        val transportType = prefs.activeTransportType
        val device = prefs.pairedDevice
        val webhook = prefs.webhookConfig

        val capable = when {
            device != null && device.address == deviceAddress   -> device
            webhook != null && deviceAddress == WEBHOOK_PSEUDO_ADDRESS -> webhook
            else -> null
        }
        if (capable == null) {
            GeofenceLogger.w(context, TAG, "ENTER for $deviceAddress — not the active transport (BLE=${device?.address} webhook=${webhook != null}), dropping")
            return
        }
        if (!capable.isGeofenceActive) {
            GeofenceLogger.d(context, TAG, "ENTER for $deviceAddress — geofence inactive (hasGeofence=${capable.hasGeofence} enabled=${capable.geofenceEnabled}), dropping")
            return
        }

        // Gate 1 — AA connected. This reflects OS-level Android Auto
        // projection state (CarConnection), independent of transport type —
        // a webhook-only user can just as well be projecting to a car head
        // unit, so this gate applies to both transports equally.
        val aaConnected = com.dunnowsoftware.GarageAAtoESP32.AndroidAutoState.isConnected
        if (aaConnected) {
            GeofenceLogger.i(context, TAG, "ENTER — device=$deviceAddress AA connected=true — Gate 1 PASS")
            fireIfNotDebounced(context, deviceAddress, gateDetail = "AA_CONNECTED")
            return
        }

        // Gates 2-4 run off the main thread (network/IO calls).
        val enterSpeed = event.triggeringLocation?.speed ?: -1f
        val triggerSpeedKmh = if (enterSpeed >= 0f) enterSpeed * 3.6f else -1f
        GeofenceLogger.i(context, TAG, "ENTER — device=$deviceAddress transport=$transportType triggerSpeed=${if (triggerSpeedKmh >= 0) "%.1f km/h".format(triggerSpeedKmh) else "unknown"}")
        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                checkFallbackGatesAndFire(context, deviceAddress, triggerSpeedKmh)
            } finally {
                pending.finish()
            }
        }
    }

    private fun checkFallbackGatesAndFire(context: Context, deviceAddress: String, triggerSpeedKmh: Float) {
        val prefs = DevicePreferences(context)
        val deviceName = prefs.pairedDevice?.name ?: prefs.webhookConfig?.name

        // Gate 2: triggerSpeed — free, already in the geofence event.
        if (triggerSpeedKmh >= MIN_TRIGGER_SPEED_KMH) {
            GeofenceLogger.i(context, TAG, "Gate 2 triggerSpeed PASS (%.1f km/h)".format(triggerSpeedKmh))
            fireIfNotDebounced(context, deviceAddress, gateDetail = "SPEED")
            return
        }

        // Gate 3: activity recognition — cached by ActivityUpdateReceiver, no I/O needed.
        val activityType = ActivityUpdateReceiver.lastActivityType(context)
        val confidence = ActivityUpdateReceiver.lastActivityConfidence(context)
        val activityAgeMs = ActivityUpdateReceiver.lastActivityAgeMs(context)
        val activityName = when (activityType) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT    -> "ON_FOOT"
            DetectedActivity.STILL      -> "STILL"
            DetectedActivity.RUNNING    -> "RUNNING"
            DetectedActivity.WALKING    -> "WALKING"
            else -> "UNKNOWN"
        }
        val activityAgeStr = if (activityAgeMs >= 0) "${activityAgeMs / 1000}s ago" else "never"
        GeofenceLogger.d(context, TAG, "Gate 3 activity — $activityName confidence=$confidence% ($activityAgeStr)")
        if ((activityType == DetectedActivity.IN_VEHICLE || activityType == DetectedActivity.ON_BICYCLE) && confidence >= 50) {
            GeofenceLogger.i(context, TAG, "Gate 3 activity PASS ($activityName $confidence% $activityAgeStr)")
            fireIfNotDebounced(context, deviceAddress, gateDetail = activityName)
            return
        }

        // Gate 4: lastLocation speed — useful only if navigation was recently active.
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val lastLoc = Tasks.await(fusedClient.lastLocation, 500, TimeUnit.MILLISECONDS)
            val ageMs = if (lastLoc != null) System.currentTimeMillis() - lastLoc.time else -1L
            val rawSpeedKmh = if (lastLoc != null && lastLoc.hasSpeed()) lastLoc.speed * 3.6f else -1f
            val lastSpeedKmh = if (rawSpeedKmh >= 0f && ageMs in 0..600_000L) rawSpeedKmh else -1f
            val ageStr = if (ageMs >= 0) "${ageMs / 1000}s" else "unavailable"
            val speedStr = when {
                lastLoc == null -> "unavailable (no fix)"
                !lastLoc.hasSpeed() -> "unavailable (no speed) age: $ageStr"
                ageMs !in 0..600_000L -> "stale (%.1f km/h age: $ageStr > 10min)".format(rawSpeedKmh)
                else -> "%.1f km/h age: $ageStr".format(lastSpeedKmh)
            }
            GeofenceLogger.d(context, TAG, "Gate 4 lastLocation — speed: $speedStr")
            if (lastSpeedKmh >= MIN_TRIGGER_SPEED_KMH) {
                GeofenceLogger.i(context, TAG, "Gate 4 lastLocation speed PASS (%.1f km/h, %ds old)".format(lastSpeedKmh, ageMs / 1000))
                fireIfNotDebounced(context, deviceAddress, gateDetail = "LAST_LOCATION_SPEED")
            } else {
                val speedToken = if (triggerSpeedKmh >= 0) "%.1f".format(triggerSpeedKmh) else "-1"
                val suppressToken = "SUPPRESSED_V2:$activityName:$confidence:$speedToken"
                GeofenceLogger.i(context, TAG, "ENTER suppressed: AA not connected, triggerSpeed ${if (triggerSpeedKmh >= 0) "%.1f km/h".format(triggerSpeedKmh) else "unknown"}, activity=$activityName($confidence%), lastLocation=$speedStr — all gates failed")
                if (deviceName != null) {
                    OpenHistoryStore.append(
                        context,
                        OpenHistoryEntry(
                            timestampMs   = System.currentTimeMillis(),
                            deviceAddress = deviceAddress,
                            deviceName    = deviceName,
                            trigger       = TriggerSource.AUTO_GEOFENCE,
                            outcome       = OpenOutcome.SUPPRESSED,
                            detail        = suppressToken,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            val speedToken = if (triggerSpeedKmh >= 0) "%.1f".format(triggerSpeedKmh) else "-1"
            val suppressToken = "SUPPRESSED_V2:$activityName:$confidence:$speedToken"
            GeofenceLogger.w(context, TAG, "ENTER suppressed: lastLocation query failed (${e.message}), triggerSpeed ${if (triggerSpeedKmh >= 0) "%.1f km/h".format(triggerSpeedKmh) else "unknown"}, activity=$activityName($confidence%) — all gates failed")
            if (deviceName != null) {
                OpenHistoryStore.append(
                    context,
                    OpenHistoryEntry(
                        timestampMs   = System.currentTimeMillis(),
                        deviceAddress = deviceAddress,
                        deviceName    = deviceName,
                        trigger       = TriggerSource.AUTO_GEOFENCE,
                        outcome       = OpenOutcome.SUPPRESSED,
                        detail        = suppressToken,
                    ),
                )
            }
        }
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

    private fun fireIfNotDebounced(context: Context, deviceAddress: String, gateDetail: String?) {
        val prefs = DevicePreferences(context)
        val now = System.currentTimeMillis()

        val lastFired = prefs.lastAutoFiredAt
        val firedAgoMs = now - lastFired
        GeofenceLogger.d(context, TAG, "Debounce — last successful auto-fire ${firedAgoMs}ms ago (limit=${DEBOUNCE_MS}ms): ${if (firedAgoMs >= DEBOUNCE_MS) "PASS" else "FAIL"}")
        if (firedAgoMs < DEBOUNCE_MS) {
            GeofenceLogger.i(context, TAG, "ENTER suppressed: debounce — fired ${firedAgoMs}ms ago")
            return
        }

        GeofenceLogger.i(context, TAG, "All gates passed for $deviceAddress — starting foreground service")
        stopGpsWarmup(context)
        val serviceIntent = Intent(context, GeofenceForegroundService::class.java).apply {
            putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
            if (gateDetail != null) putExtra(GeofenceForegroundService.EXTRA_GATE_DETAIL, gateDetail)
        }
        context.startForegroundService(serviceIntent)
    }
}

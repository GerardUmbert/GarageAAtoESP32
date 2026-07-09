package com.dunnowsoftware.GarageAAtoESP32.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.dunnowsoftware.GarageAAtoESP32.data.GarageDevice
import com.dunnowsoftware.GarageAAtoESP32.data.GeofenceCapable
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

private const val TAG = "GeofenceManager"
const val GEOFENCE_ID_PREFIX = "garage_"
const val GEOFENCE_OUTER_ID_PREFIX = "garage_outer_"
const val EXTRA_DEVICE_ADDRESS = "device_address"
// Outer geofence offset for GPS warmup — added on top of the user-configured radius
const val OUTER_GEOFENCE_OFFSET_M = 150f

class GeofenceManager(private val context: Context) {

    private val client = LocationServices.getGeofencingClient(context)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun activityPendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityUpdateReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun startActivityUpdates() {
        try {
            ActivityRecognition.getClient(context)
                .requestActivityUpdates(30_000L, activityPendingIntent())
                .addOnSuccessListener { GeofenceLogger.i(context, TAG, "Activity updates registered (30s interval)") }
                .addOnFailureListener { GeofenceLogger.w(context, TAG, "Failed to register activity updates: $it") }
        } catch (e: SecurityException) {
            GeofenceLogger.w(context, TAG, "Missing ACTIVITY_RECOGNITION permission: $e")
        }
    }

    fun stopActivityUpdates() {
        try {
            ActivityRecognition.getClient(context)
                .removeActivityUpdates(activityPendingIntent())
                .addOnSuccessListener { GeofenceLogger.i(context, TAG, "Activity updates removed") }
                .addOnFailureListener { GeofenceLogger.w(context, TAG, "Failed to remove activity updates: $it") }
        } catch (e: SecurityException) {
            GeofenceLogger.w(context, TAG, "Missing ACTIVITY_RECOGNITION permission on stop: $e")
        }
    }

    fun register(device: GarageDevice) = register(device.addressKey, device)

    /**
     * Registers inner+outer geofences keyed on [addressKey]. [addressKey] is
     * [GarageDevice.addressKey] — the BLE MAC for BLE pairings, or the
     * device's own id for webhook configs (which have no MAC to key on).
     */
    fun register(addressKey: String, capable: GeofenceCapable) {
        val lat = capable.geofenceLat ?: return
        val lng = capable.geofenceLng ?: return
        val radius = capable.geofenceRadiusM ?: return

        val outerOffset = capable.geofenceOuterOffsetM
        GeofenceLogger.i(context, TAG, "Registering geofence for $addressKey — lat=$lat lng=$lng inner=${radius}m outer=${radius + outerOffset}m")

        val innerGeofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID_PREFIX + addressKey)
            .setCircularRegion(lat, lng, radius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setNotificationResponsiveness(5_000)
            .build()

        val outerGeofence = Geofence.Builder()
            .setRequestId(GEOFENCE_OUTER_ID_PREFIX + addressKey)
            .setCircularRegion(lat, lng, radius + outerOffset)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setNotificationResponsiveness(5_000)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // do not fire immediately on register
            .addGeofence(innerGeofence)
            .addGeofence(outerGeofence)
            .build()

        try {
            client.addGeofences(request, pendingIntent())
                .addOnSuccessListener { GeofenceLogger.i(context, TAG, "Geofence registered OK for $addressKey") }
                .addOnFailureListener { GeofenceLogger.e(context, TAG, "Failed to register geofence for $addressKey: $it") }
        } catch (e: SecurityException) {
            GeofenceLogger.e(context, TAG, "Missing location permission for geofence registration: $e")
        }
        startActivityUpdates()
    }

    fun unregister(deviceAddress: String) {
        GeofenceLogger.i(context, TAG, "Removing geofence for $deviceAddress")
        client.removeGeofences(listOf(GEOFENCE_ID_PREFIX + deviceAddress, GEOFENCE_OUTER_ID_PREFIX + deviceAddress))
            .addOnSuccessListener { GeofenceLogger.i(context, TAG, "Geofence removed OK for $deviceAddress") }
            .addOnFailureListener { GeofenceLogger.e(context, TAG, "Failed to remove geofence for $deviceAddress: $it") }
        stopActivityUpdates()
    }

    fun reregisterAll() {
        val devices = DevicePreferences(context).devices
        if (devices.isEmpty()) {
            GeofenceLogger.d(context, TAG, "reregisterAll: nothing paired, nothing to register")
            return
        }
        val active = devices.filter { it.isGeofenceActive }
        if (active.isEmpty()) {
            GeofenceLogger.d(context, TAG, "reregisterAll: no device has an active geofence, skipping")
            return
        }
        active.forEach { device ->
            GeofenceLogger.i(context, TAG, "reregisterAll: re-registering geofence for ${device.addressKey}")
            register(device) // also calls startActivityUpdates()
        }
    }
}

package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Context
import com.dunnowsoftware.GarageAAtoESP32.data.DevicePreferences
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors

private const val PATH_RESULT    = "/garage/result"
private const val PATH_AUTOFIRED = "/garage/autofired"
private const val PATH_SENDING   = "/garage/sending"

private val executor = Executors.newSingleThreadExecutor()

fun notifyWatchSending(context: Context) {
    DevicePreferences(context).lastSendingAt = System.currentTimeMillis()
    sendToWatch(context, PATH_SENDING, ByteArray(0))
}

fun notifyWatchAutoFired(context: Context) {
    sendToWatch(context, PATH_AUTOFIRED, ByteArray(0))
}

fun notifyWatchResult(context: Context, success: Boolean) {
    val ts = System.currentTimeMillis()
    val prefs = DevicePreferences(context)
    if (success) prefs.lastAutoFiredAt = ts else prefs.lastAutoFailedAt = ts
    val payload = (if (success) "SUCCESS" else "FAIL").toByteArray()
    sendToWatch(context, PATH_RESULT, payload)
}


private fun sendToWatch(context: Context, path: String, payload: ByteArray) {
    executor.execute {
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            nodes.forEach { node ->
                Tasks.await(
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, path, payload)
                )
            }
        } catch (_: Exception) {}
    }
}

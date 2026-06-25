package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors

private const val PATH_RESULT   = "/garage/result"
private const val PATH_AUTOFIRED = "/garage/autofired"

private val executor = Executors.newSingleThreadExecutor()

fun notifyWatchAutoFired(context: Context) {
    sendToWatch(context, PATH_AUTOFIRED, ByteArray(0))
}

fun notifyWatchResult(context: Context, success: Boolean) {
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

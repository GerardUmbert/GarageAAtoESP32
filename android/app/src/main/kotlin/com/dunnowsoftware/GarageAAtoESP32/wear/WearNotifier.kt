package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors

private val executor = Executors.newSingleThreadExecutor()

fun notifyWatchAutoFired(context: Context) {
    executor.execute {
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            nodes.forEach { node ->
                Tasks.await(
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, PATH_AUTOFIRED, ByteArray(0))
                )
            }
        } catch (_: Exception) {}
    }
}

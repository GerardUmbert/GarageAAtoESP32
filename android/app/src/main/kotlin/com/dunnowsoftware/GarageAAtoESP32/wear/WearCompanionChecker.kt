package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WEAR_CAPABILITY = "garage_opener_wear"
private const val WEAR_PACKAGE    = "com.dunnowsoftware.GarageAAtoESP32"

suspend fun hasWatchPairedButNotInstalled(context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
        if (nodes.isEmpty()) return@withContext false
        val result = Tasks.await(
            Wearable.getCapabilityClient(context).getCapability(
                WEAR_CAPABILITY,
                CapabilityClient.FILTER_REACHABLE,
            )
        )
        result.nodes.isEmpty()
    } catch (_: Exception) {
        false
    }
}

fun installWearCompanion(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("market://details?id=$WEAR_PACKAGE")
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    val helper = RemoteActivityHelper(context)
    Wearable.getNodeClient(context).connectedNodes
        .addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                helper.startRemoteActivity(intent, node.id)
            }
        }
}

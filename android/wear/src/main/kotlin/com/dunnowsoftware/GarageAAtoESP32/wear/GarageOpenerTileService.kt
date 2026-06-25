package com.dunnowsoftware.GarageAAtoESP32.wear

import android.content.Context
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.tiles.TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.tiles.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.tiles.DimensionBuilders.expand
import androidx.wear.tiles.DimensionBuilders.sp
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ResourceBuilders
import com.dunnowsoftware.GarageAAtoESP32.R
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

private const val PATH_OPEN = "/garage/open"
private const val CLICKABLE_ID = "open"
private val executor = Executors.newSingleThreadExecutor()

class GarageOpenerTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        if (requestParams.currentState.lastClickableId == CLICKABLE_ID &&
            TileStateStore.get(this) == TileState.Idle
        ) {
            fireOpen(this)
        }

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                androidx.wear.tiles.LayoutElementBuilders.Layout.Builder()
                                    .setRoot(buildRoot())
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        return ResolvableFuture.create<TileBuilders.Tile>().also { it.set(tile) }
    }

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        ResolvableFuture.create<ResourceBuilders.Resources>().also {
            it.set(ResourceBuilders.Resources.Builder().setVersion("1").build())
        }

    private fun buildRoot(): LayoutElementBuilders.LayoutElement {
        val state = TileStateStore.get(this)
        val label = when (state) {
            TileState.Idle    -> getString(R.string.tile_open)
            TileState.Sending -> getString(R.string.tile_opening)
            TileState.Opened  -> getString(R.string.tile_opened)
            TileState.Failed  -> getString(R.string.tile_failed)
        }
        val color = when (state) {
            TileState.Idle    -> 0xFFEAECED.toInt()
            TileState.Sending -> 0xFF2AD4A3.toInt()
            TileState.Opened  -> 0xFF2AD4A3.toInt()
            TileState.Failed  -> 0xFFFF6B6B.toInt()
        }

        val textElement = LayoutElementBuilders.Text.Builder()
            .setText(label)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setColor(argb(color))
                    .setSize(sp(16f))
                    .setWeight(700)
                    .build()
            )
            .build()

        val boxBuilder = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(textElement)

        if (state == TileState.Idle) {
            boxBuilder.setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(CLICKABLE_ID)
                            .setOnClick(
                                ActionBuilders.LoadAction.Builder()
                                    .setRequestState(
                                        androidx.wear.tiles.StateBuilders.State.Builder().build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        }

        return boxBuilder.build()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            getUpdater(context).requestUpdate(GarageOpenerTileService::class.java)
        }

        fun fireOpen(context: Context) {
            TileStateStore.setSending(context)
            requestTileUpdate(context)
            executor.execute {
                try {
                    val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                    if (nodes.isEmpty()) {
                        TileStateStore.setResult(context, false)
                        requestTileUpdate(context)
                        return@execute
                    }
                    nodes.forEach { node ->
                        Tasks.await(
                            Wearable.getMessageClient(context)
                                .sendMessage(node.id, PATH_OPEN, ByteArray(0))
                        )
                    }
                } catch (_: Exception) {
                    TileStateStore.setResult(context, false)
                    requestTileUpdate(context)
                }
            }
        }
    }
}

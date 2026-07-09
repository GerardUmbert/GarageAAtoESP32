package com.dunnowsoftware.GarageAAtoESP32.wear

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.dunnowsoftware.GarageAAtoESP32.R
import com.google.common.util.concurrent.ListenableFuture

private const val CLICKABLE_ID = "open"
private const val RES_ICON     = "ic_tile"
private const val RING_SIZE_DP = 112f

/**
 * A garage tile is a plain launch button. This OEM watch (com.oplus.wearable.sysui) ignores
 * background tile refreshes — both requestUpdate() and short freshness intervals are throttled —
 * so a tile cannot repaint itself to show an async OPENING/OPENED result. Instead, tapping the
 * tile launches WearActivity with EXTRA_AUTO_OPEN, which fires the open and shows the full
 * animated feedback (and haptics) that the app already does reliably — unless 2+ devices are
 * paired, in which case it lands on the device picker instead of guessing which one to fire.
 */
class GarageOpenerTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_ICON)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(buildRoot())
            )
            .build()
        return ResolvableFuture.create<TileBuilders.Tile>().also { it.set(tile) }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RES_ICON)
            .addIdToImageMapping(
                RES_ICON,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.ic_tile)
                            .build()
                    )
                    .build()
            )
            .build()
        return ResolvableFuture.create<ResourceBuilders.Resources>().also { it.set(resources) }
    }

    private fun buildRoot(): LayoutElementBuilders.LayoutElement {
        val icon = Image.Builder()
            .setResourceId(RES_ICON)
            .setWidth(dp(40f))
            .setHeight(dp(40f))
            .build()

        val label = Text.Builder(this, getString(R.string.tile_open))
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(argb(0xFFEAECED.toInt()))
            .build()

        val content = Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(icon)
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(dp(6f))
                    .build()
            )
            .addContent(label)
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(CLICKABLE_ID)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearActivity::class.java.name)
                            .addKeyToExtraMapping(
                                WearActivity.EXTRA_AUTO_OPEN,
                                ActionBuilders.AndroidBooleanExtra.Builder()
                                    .setValue(true)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        // Circular ring to match the watch app's idle hero button (2dp near-white border).
        val ring = Box.Builder()
            .setWidth(dp(RING_SIZE_DP))
            .setHeight(dp(RING_SIZE_DP))
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(content)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(RING_SIZE_DP / 2f))
                                    .build()
                            )
                            .build()
                    )
                    .setBorder(
                        ModifiersBuilders.Border.Builder()
                            .setWidth(dp(2f))
                            .setColor(argb(0xFFEAECED.toInt()))
                            .build()
                    )
                    .setClickable(clickable)
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(ring)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF0A0C0E.toInt()))
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

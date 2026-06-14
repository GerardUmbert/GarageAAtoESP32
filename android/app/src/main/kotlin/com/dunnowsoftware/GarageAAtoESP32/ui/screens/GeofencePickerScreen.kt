package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.location.Location
import androidx.preference.PreferenceManager
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ui.HAPTIC_TAP
import com.dunnowsoftware.GarageAAtoESP32.ui.vibrate
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.abs
import kotlin.math.roundToInt

private val CartoDarkTiles = object : OnlineTileSourceBase(
    "CartoDark",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
    ),
    "© OpenStreetMap contributors, © CARTO",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
    ),
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
    }
}

private const val ACCENT_ARGB            = 0xFF2AD4A3.toInt()
private const val ACCENT_FILL_ARGB       = 0x302AD4A3
private const val ACCENT_OUTER_ARGB      = 0x662AD4A3  // ~40% alpha for the warmup ring
private const val OUTER_GEOFENCE_OFFSET  = 150.0       // must match GeofenceManager.OUTER_GEOFENCE_OFFSET_M

private const val RADIUS_MIN     = 15f
private const val RADIUS_MAX     = 75f
private const val RADIUS_DEFAULT = 40f
private const val DEFAULT_ZOOM   = 17.0  // used only when centering on user location with no pin
private const val CIRCLE_PADDING = 0.55  // fraction of map viewport the circle should fill

private fun formatCoord(deg: Double, posLabel: String, negLabel: String): String {
    val d = abs(deg)
    val label = if (deg >= 0) posLabel else negLabel
    return "%.4f° %s".format(d, label)
}

@Composable
fun GeofencePickerScreen(
    initialLat: Double?,
    initialLng: Double?,
    initialRadiusM: Float?,
    deviceName: String?,
    deviceAddress: String?,
    onSave: (lat: Double, lng: Double, radiusM: Float) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            load(context, PreferenceManager.getDefaultSharedPreferences(context))
            userAgentValue = context.packageName
        }
    }

    var pickedPoint by remember {
        mutableStateOf(
            if (initialLat != null && initialLng != null) GeoPoint(initialLat, initialLng) else null
        )
    }
    var radiusM by remember { mutableFloatStateOf(initialRadiusM ?: RADIUS_DEFAULT) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var geofencePolygon by remember { mutableStateOf<Polygon?>(null) }
    var outerPolygon by remember { mutableStateOf<Polygon?>(null) }

    fun updateCircle(center: GeoPoint, map: MapView) {
        geofencePolygon?.let { map.overlays.remove(it) }
        outerPolygon?.let { map.overlays.remove(it) }

        val outer = Polygon(map).apply {
            points = Polygon.pointsAsCircle(center, radiusM.toDouble() + OUTER_GEOFENCE_OFFSET)
            fillPaint.apply {
                color = 0x002AD4A3  // transparent fill
                style = Paint.Style.FILL
            }
            outlinePaint.apply {
                color = ACCENT_OUTER_ARGB
                strokeWidth = 2f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
            }
            isVisible = true
        }
        map.overlays.add(outer)
        outerPolygon = outer

        val poly = Polygon(map).apply {
            points = Polygon.pointsAsCircle(center, radiusM.toDouble())
            fillPaint.apply {
                color = ACCENT_FILL_ARGB
                style = Paint.Style.FILL
            }
            outlinePaint.apply {
                color = ACCENT_ARGB
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            isVisible = true
        }
        map.overlays.add(poly)
        geofencePolygon = poly
        map.invalidate()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg),
    ) {
        // Top bar with Save action on the right
        val canSave = pickedPoint != null
        val ctx = LocalContext.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            // Back button (left)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { vibrate(ctx, HAPTIC_TAP); onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "‹",
                    color = GarageColors.Text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 28.sp,
                )
            }
            // Save button (right)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (canSave) GarageColors.Accent else Color.Transparent)
                    .clickable(enabled = canSave) {
                        vibrate(ctx, HAPTIC_TAP)
                        onSave(pickedPoint!!.latitude, pickedPoint!!.longitude, radiusM)
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.geofence_picker_save),
                    color = if (canSave) GarageColors.AccentDeep else GarageColors.TextFaint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Text(
            text = stringResource(R.string.geofence_picker_title),
            color = GarageColors.Text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.geofence_picker_subtitle),
            color = GarageColors.TextDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
        )

        // Map with floating overlays
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapCtx ->
                    MapView(mapCtx).apply {
                        setTileSource(CartoDarkTiles)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(DEFAULT_ZOOM)
                        clipToOutline = true

                        val startPoint = pickedPoint
                        if (startPoint != null) {
                            // Zoom so the circle fills ~55% of the viewport on first open.
                            // zoomToBoundingBox needs the map to have a measured size, so
                            // post it to run after the first layout pass.
                            val circleBbox = org.osmdroid.util.BoundingBox.fromGeoPoints(
                                Polygon.pointsAsCircle(startPoint, radiusM.toDouble())
                            )
                            post {
                                controller.setCenter(startPoint)
                                zoomToBoundingBox(
                                    circleBbox.increaseByScale((1.0 / CIRCLE_PADDING).toFloat()),
                                    false,
                                )
                            }
                            updateCircle(startPoint, this)
                        } else {
                            try {
                                LocationServices.getFusedLocationProviderClient(mapCtx)
                                    .lastLocation
                                    .addOnSuccessListener { loc: Location? ->
                                        if (loc != null) {
                                            controller.setZoom(DEFAULT_ZOOM)
                                            controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                        }
                                    }
                            } catch (_: SecurityException) {
                                controller.setZoom(DEFAULT_ZOOM)
                            }
                        }

                        val myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(mapCtx), this)
                        myLocOverlay.enableMyLocation()
                        overlays.add(myLocOverlay)

                        overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                                val proj = mapView.projection
                                val tapped = proj.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                                pickedPoint = tapped
                                updateCircle(tapped, mapView)
                                return true
                            }
                        })

                        mapViewRef = this
                    }
                },
                update = { map ->
                    val point = pickedPoint ?: return@AndroidView
                    updateCircle(point, map)
                },
            )

            // Lightening scrim — sits above map tiles, below all overlaid UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFB0C8E8).copy(alpha = 0.12f)),
            )

            // Radius pill — top-left
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GarageColors.Bg.copy(alpha = 0.82f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(GarageColors.Accent),
                )
                Text(
                    text = "${radiusM.roundToInt()} m ${stringResource(R.string.geofence_picker_radius_pill_suffix)}",
                    color = GarageColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Coordinates chip — bottom-center, only when pin placed
            val point = pickedPoint
            if (point != null) {
                val latStr = formatCoord(point.latitude, "N", "S")
                val lngStr = formatCoord(point.longitude, "E", "W")
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GarageColors.Bg.copy(alpha = 0.82f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "$latStr  $lngStr",
                        color = GarageColors.TextDim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Radius slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.geofence_picker_radius_label),
                    color = GarageColors.TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = "${RADIUS_MIN.roundToInt()} m — ${RADIUS_MAX.roundToInt()} m",
                    color = GarageColors.TextFaint,
                    fontSize = 12.sp,
                )
            }
            @OptIn(ExperimentalMaterial3Api::class)
            Slider(
                value = radiusM,
                onValueChange = { v ->
                    radiusM = v
                    val p = pickedPoint ?: return@Slider
                    val map = mapViewRef ?: return@Slider
                    updateCircle(p, map)
                },
                valueRange = RADIUS_MIN..RADIUS_MAX,
                colors = SliderDefaults.colors(
                    thumbColor = GarageColors.Accent,
                    activeTrackColor = GarageColors.Accent,
                    inactiveTrackColor = GarageColors.Surface2,
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Paired device card at the bottom
        if (deviceAddress != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GarageColors.Surface)
                    .border(1.dp, GarageColors.Hairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = deviceName ?: stringResource(R.string.settings_device_default_name),
                    color = GarageColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = deviceAddress,
                    color = GarageColors.TextDim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }
    }
}

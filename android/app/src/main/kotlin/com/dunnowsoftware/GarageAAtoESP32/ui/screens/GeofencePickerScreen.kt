package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.dunnowsoftware.GarageAAtoESP32.BuildConfig
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ui.HAPTIC_TAP
import com.dunnowsoftware.GarageAAtoESP32.ui.vibrate
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors
import com.google.android.gms.location.LocationServices
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

// CARTO requires an API key; builds without one (forks) fall back to plain OSM raster tiles.
private val MapStyleUrl = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json?key=${BuildConfig.CARTO_API_KEY}"
private val HasCartoKey = BuildConfig.CARTO_API_KEY.isNotBlank()
private const val OsmFallbackStyle = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":19,"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""

private const val SRC_INNER = "geofence-inner"
private const val SRC_OUTER = "geofence-outer"

/** Circle as a GeoJSON polygon (flat-earth approximation, accurate enough at geofence scale). */
private fun circlePoints(center: LatLng, radiusM: Double): List<Point> {
    val dLat = radiusM / 111_320.0
    val dLng = radiusM / (111_320.0 * cos(Math.toRadians(center.latitude)))
    return (0..72).map { i ->
        val a = Math.toRadians(i * 5.0)
        Point.fromLngLat(center.longitude + dLng * cos(a), center.latitude + dLat * sin(a))
    }
}

private fun circlePolygon(center: LatLng, radiusM: Double): Polygon =
    Polygon.fromLngLats(listOf(circlePoints(center, radiusM)))

private const val ACCENT_ARGB            = 0xFF2AD4A3.toInt()
private const val ACCENT_OUTER_ARGB      = 0x662AD4A3  // ~40% alpha for the warmup ring

private const val RADIUS_MIN        = 15f
private const val RADIUS_MAX        = 40f
private const val RADIUS_DEFAULT    = 20f
private const val OUTER_OFFSET_MIN  = 250f
private const val OUTER_OFFSET_MAX  = 1000f
private const val OUTER_OFFSET_DEFAULT = 400f
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
    initialOuterOffsetM: Float?,
    deviceName: String?,
    deviceAddress: String?,
    onSave: (lat: Double, lng: Double, radiusM: Float, outerOffsetM: Float) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var pickedPoint by remember {
        mutableStateOf(
            if (initialLat != null && initialLng != null) LatLng(initialLat, initialLng) else null
        )
    }
    var radiusM by remember { mutableFloatStateOf(initialRadiusM ?: RADIUS_DEFAULT) }
    var outerOffsetM by remember { mutableFloatStateOf(initialOuterOffsetM ?: OUTER_OFFSET_DEFAULT) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    fun updateCircle(center: LatLng, map: MapLibreMap) {
        val style = map.style ?: return
        style.getSourceAs<GeoJsonSource>(SRC_INNER)
            ?.setGeoJson(Feature.fromGeometry(circlePolygon(center, radiusM.toDouble())))
        style.getSourceAs<GeoJsonSource>(SRC_OUTER)
            ?.setGeoJson(Feature.fromGeometry(circlePolygon(center, radiusM.toDouble() + outerOffsetM.toDouble())))
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
                        onSave(pickedPoint!!.latitude, pickedPoint!!.longitude, radiusM, outerOffsetM)
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
            val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
            val mapView = remember {
                MapLibre.getInstance(context)
                MapView(context)
            }
            DisposableEffect(lifecycle, mapView) {
                mapView.onCreate(null)
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> mapView.onStart()
                        Lifecycle.Event.ON_RESUME -> mapView.onResume()
                        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                        Lifecycle.Event.ON_STOP -> mapView.onStop()
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                // The effect may start after the lifecycle already reached STARTED/RESUMED.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
                onDispose {
                    lifecycle.removeObserver(observer)
                    mapView.onPause()
                    mapView.onStop()
                    mapView.onDestroy()
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    mapView.apply {
                        getMapAsync { map ->
                            map.uiSettings.isCompassEnabled = false
                            map.uiSettings.isRotateGesturesEnabled = false
                            map.uiSettings.isTiltGesturesEnabled = false

                            val builder = if (HasCartoKey) Style.Builder().fromUri(MapStyleUrl)
                                          else Style.Builder().fromJson(OsmFallbackStyle)
                            map.setStyle(builder) { style ->
                                val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
                                style.addSource(GeoJsonSource(SRC_OUTER, empty))
                                style.addSource(GeoJsonSource(SRC_INNER, empty))
                                style.addLayer(
                                    LineLayer("geofence-outer-line", SRC_OUTER).withProperties(
                                        PropertyFactory.lineColor(ACCENT_OUTER_ARGB),
                                        PropertyFactory.lineWidth(2f),
                                        PropertyFactory.lineDasharray(arrayOf(5f, 4f)),
                                    )
                                )
                                style.addLayer(
                                    FillLayer("geofence-inner-fill", SRC_INNER).withProperties(
                                        PropertyFactory.fillColor(ACCENT_ARGB),
                                        PropertyFactory.fillOpacity(0.19f),
                                    )
                                )
                                style.addLayer(
                                    LineLayer("geofence-inner-line", SRC_INNER).withProperties(
                                        PropertyFactory.lineColor(ACCENT_ARGB),
                                        PropertyFactory.lineWidth(3f),
                                    )
                                )

                                enableUserLocation(context, map, style)

                                val startPoint = pickedPoint
                                if (startPoint != null) {
                                    updateCircle(startPoint, map)
                                    // Zoom so the circle fills ~55% of the viewport on first open.
                                    val bounds = LatLngBounds.Builder()
                                        .includes(circlePoints(startPoint, radiusM / CIRCLE_PADDING)
                                            .map { LatLng(it.latitude(), it.longitude()) })
                                        .build()
                                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))
                                } else {
                                    try {
                                        LocationServices.getFusedLocationProviderClient(context)
                                            .lastLocation
                                            .addOnSuccessListener { loc: Location? ->
                                                if (loc != null) {
                                                    map.animateCamera(
                                                        CameraUpdateFactory.newLatLngZoom(
                                                            LatLng(loc.latitude, loc.longitude), DEFAULT_ZOOM,
                                                        )
                                                    )
                                                }
                                            }
                                    } catch (_: SecurityException) {
                                        map.moveCamera(CameraUpdateFactory.zoomTo(DEFAULT_ZOOM))
                                    }
                                }
                            }

                            map.addOnMapClickListener { tapped ->
                                pickedPoint = tapped
                                updateCircle(tapped, map)
                                true
                            }
                            mapRef = map
                        }
                    }
                },
                update = {
                    val point = pickedPoint ?: return@AndroidView
                    mapRef?.let { updateCircle(point, it) }
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
                Text(
                    text = "·",
                    color = GarageColors.TextFaint,
                    fontSize = 13.sp,
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(ACCENT_OUTER_ARGB)),
                )
                Text(
                    text = "${(radiusM + outerOffsetM).roundToInt()} m ${stringResource(R.string.geofence_picker_outer_pill_suffix)}",
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
                    val map = mapRef ?: return@Slider
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

        Spacer(Modifier.height(8.dp))

        // Outer geofence (warmup ring) slider
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
                    text = stringResource(R.string.geofence_picker_outer_label),
                    color = GarageColors.TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = "${OUTER_OFFSET_MIN.roundToInt()} m — ${OUTER_OFFSET_MAX.roundToInt()} m",
                    color = GarageColors.TextFaint,
                    fontSize = 12.sp,
                )
            }
            @OptIn(ExperimentalMaterial3Api::class)
            Slider(
                value = outerOffsetM,
                onValueChange = { v ->
                    outerOffsetM = v
                    val p = pickedPoint ?: return@Slider
                    val map = mapRef ?: return@Slider
                    updateCircle(p, map)
                },
                valueRange = OUTER_OFFSET_MIN..OUTER_OFFSET_MAX,
                colors = SliderDefaults.colors(
                    thumbColor = Color(ACCENT_OUTER_ARGB),
                    activeTrackColor = Color(ACCENT_OUTER_ARGB),
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

@SuppressLint("MissingPermission")
private fun enableUserLocation(context: android.content.Context, map: MapLibreMap, style: Style) {
    val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!granted) return
    map.locationComponent.apply {
        activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        isLocationComponentEnabled = true
    }
}

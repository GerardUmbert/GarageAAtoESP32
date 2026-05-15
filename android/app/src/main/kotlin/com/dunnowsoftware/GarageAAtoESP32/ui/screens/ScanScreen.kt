package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ui.HAPTIC_TAP
import com.dunnowsoftware.GarageAAtoESP32.ui.vibrate
import com.dunnowsoftware.GarageAAtoESP32.ble.BleScanner
import com.dunnowsoftware.GarageAAtoESP32.ble.FoundDevice
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

@Composable
fun ScanScreen(
    onPicked: (FoundDevice) -> Unit,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    excludeAddress: String? = null,
) {
    val ctx = LocalContext.current
    val scanner = remember { BleScanner(ctx) }
    val devices = remember { mutableStateListOf<FoundDevice>() }
    var hasPermission by remember { mutableStateOf(checkBlePermissions(ctx)) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.all { it }
        if (!hasPermission) {
            scanError = "Bluetooth permission is required to find your opener."
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            scanError = ctx.getString(R.string.scan_permission_error)
            permissionLauncher.launch(blePermissions())
            return@LaunchedEffect
        }
        scanError = null
        devices.clear()
        try {
            scanner.start { dev ->
                if (dev.address == excludeAddress) return@start
                val idx = devices.indexOfFirst { it.address == dev.address }
                if (idx == -1) devices.add(dev) else devices[idx] = dev
            }
        } catch (t: Throwable) {
            scanError = ctx.getString(R.string.scan_error, t.message ?: ctx.getString(R.string.scan_unknown_error))
        }
    }

    DisposableEffect(Unit) {
        onDispose { scanner.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp),
    ) {
        TopBar(onBack = onBack, parentHorizontalPadding = 32.dp)

        Text(
            text = stringResource(R.string.scan_title),
            color = GarageColors.Text,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (scanError != null) scanError!! else stringResource(R.string.scan_subtitle),
            color = if (scanError != null) GarageColors.Danger else GarageColors.TextDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Radar(devices = devices)
        }

        FoundDeviceCard(
            devices = devices,
            onPicked = onPicked,
        )

        if (onSkip != null) {
            val ctxSkip = LocalContext.current
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.scan_skip),
                    color = GarageColors.TextDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            vibrate(ctxSkip, HAPTIC_TAP)
                            onSkip()
                        }
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun Radar(devices: List<FoundDevice> = emptyList()) {
    val infinite = rememberInfiniteTransition(label = "radar")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
        ),
        label = "sweep",
    )

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 5 concentric circles, evenly spaced.
        // Diameters step by 240/5 = 48dp: 48, 96, 144, 192, 240.
        // Innermost is the green accent indicator; the other four are subtle
        // white rings (slightly more visible toward the outside).
        listOf(2, 3, 4, 5).forEachIndexed { i, step ->
            Box(
                modifier = Modifier
                    .size((48 * step).dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.08f + i * 0.03f), CircleShape),
            )
        }

        // sweep
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .rotate(angle),
        ) {
            val brush = Brush.sweepGradient(
                0.00f to Color.Transparent,
                0.16f to GarageColors.Accent.copy(alpha = 0.30f),
                0.25f to Color.Transparent,
                1.00f to Color.Transparent,
                center = Offset(size.width / 2f, size.height / 2f),
            )
            drawCircle(brush = brush, radius = size.minDimension / 2f)
        }

        // Device blips — one accent dot per discovered device, placed at a
        // radius driven by RSSI (stronger → closer to centre) and a stable
        // random angle per address.
        devices.forEach { dev ->
            DeviceBlip(rssi = dev.rssi, addressSeed = dev.address)
        }

        // Innermost green dot — fills the 48dp slot, sharing the same spacing
        // cadence as the four outer rings.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(GarageColors.AccentSoft)
                .border(1.dp, GarageColors.AccentLine, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(GarageColors.Accent),
            )
        }
    }
}

@Composable
private fun DeviceBlip(rssi: Int, addressSeed: String) {
    // Radar is 240dp diameter → 120dp radius. Keep blips between the inner
    // green indicator and the outermost ring.
    val minRadiusDp = 32f   // just outside the centre indicator
    val maxRadiusDp = 112f  // a hair inside the outer ring

    // RSSI clamp: -40 dBm is very close, -90 dBm is far. Anything outside
    // those bounds saturates at the edges.
    val clamped = rssi.coerceIn(-90, -40)
    val t = (-40 - clamped) / 50f  // 0f at -40 (strong), 1f at -90 (weak)
    val radiusDp = minRadiusDp + t * (maxRadiusDp - minRadiusDp)

    // Deterministic angle from the MAC so the dot doesn't dance on every
    // recomposition.
    val angleRad = (addressSeed.hashCode().toLong() and 0xFFFF) / 65535.0 * 2 * Math.PI
    val xDp = (radiusDp * kotlin.math.cos(angleRad)).toFloat()
    val yDp = (radiusDp * kotlin.math.sin(angleRad)).toFloat()

    Box(
        modifier = Modifier
            .offset(x = xDp.dp, y = yDp.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(GarageColors.Accent)
            .border(1.dp, GarageColors.AccentLine, CircleShape),
    )
}

@Composable
private fun FoundDeviceCard(
    devices: List<FoundDevice>,
    onPicked: (FoundDevice) -> Unit,
) {
    if (devices.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GarageColors.Surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.scan_scanning),
                color = GarageColors.TextDim,
                fontSize = 14.sp,
            )
        }
        return
    }

    // Cap the list height so a flood of nearby openers can't push into the
    // radar above. ~3 rows fit before scrolling kicks in.
    Column(
        modifier = Modifier
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        val ctx = LocalContext.current
        devices.forEachIndexed { idx, dev ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GarageColors.Surface)
                    .clickable {
                        vibrate(ctx, HAPTIC_TAP)
                        onPicked(dev)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GarageColors.Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(GarageColors.Accent),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dev.name,
                        color = GarageColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.scan_tap_to_pair),
                        color = GarageColors.TextDim,
                        fontSize = 13.sp,
                    )
                }
                Text(
                    text = "${dev.rssi} dBm",
                    color = GarageColors.TextDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (idx != devices.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

private fun blePermissions(): Array<String> = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private fun checkBlePermissions(ctx: android.content.Context): Boolean =
    blePermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

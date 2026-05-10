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
import com.dunnowsoftware.GarageAAtoESP32.ble.BleScanner
import com.dunnowsoftware.GarageAAtoESP32.ble.FoundDevice
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

@Composable
fun ScanScreen(
    onPicked: (FoundDevice) -> Unit,
    onBack: (() -> Unit)? = null,
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
            permissionLauncher.launch(blePermissions())
            return@LaunchedEffect
        }
        scanError = null
        devices.clear()
        try {
            scanner.start { dev ->
                if (devices.none { it.address == dev.address }) devices.add(dev)
            }
        } catch (t: Throwable) {
            scanError = "Couldn't start Bluetooth scan: ${t.message ?: "unknown error"}"
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
            text = "Looking for your\nopener…",
            color = GarageColors.Text,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (scanError != null)
                scanError!!
            else
                "Make sure the ESP32 is powered on. Stand within a few metres of it.",
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
            Radar()
        }

        FoundDeviceCard(
            devices = devices,
            onPicked = onPicked,
        )
    }
}

@Composable
private fun Radar() {
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
                text = "Scanning for openers…",
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
        devices.forEachIndexed { idx, dev ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GarageColors.Surface)
                    .clickable { onPicked(dev) }
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
                        text = "Tap to pair",
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

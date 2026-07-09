package com.dunnowsoftware.GarageAAtoESP32.wear

import com.dunnowsoftware.GarageAAtoESP32.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text

enum class WatchOpenState { Idle, Sending, Opened, Failed }

@Composable
fun WatchMainScreen(
    state: WatchOpenState,
    devices: List<WatchDevice> = emptyList(),
    selectedId: String? = null,
    onOpen: () -> Unit,
    onOpenDevice: (String) -> Unit = {},
) {
    // A picker only makes sense with 2+ known devices and while idle — mid-send/result
    // states keep showing the hero button so the tap target and feedback stay
    // where the user is already looking, same as the single-device case.
    if (devices.size > 1 && state == WatchOpenState.Idle) {
        WatchDevicePicker(devices = devices, selectedId = selectedId, onPick = onOpenDevice)
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearColors.Bg),
        contentAlignment = Alignment.Center,
    ) {
        WatchHeroButton(state = state, onClick = onOpen)
    }
}

@Composable
private fun WatchDevicePicker(
    devices: List<WatchDevice>,
    selectedId: String?,
    onPick: (String) -> Unit,
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WearColors.Bg),
    ) {
        items(devices) { device ->
            val isSelected = device.id == selectedId
            Chip(
                onClick = { onPick(device.id) },
                label = { Text(device.name.ifEmpty { stringResource(R.string.watch_device_default_name) }) },
                secondaryLabel = {
                    Text(
                        if (device.transport == WatchTransportType.WEBHOOK)
                            stringResource(R.string.watch_transport_webhook)
                        else
                            stringResource(R.string.watch_transport_ble)
                    )
                },
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (isSelected) WearColors.Surface else WearColors.Bg,
                    contentColor = WearColors.Text,
                    secondaryContentColor = WearColors.TextDim,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WatchHeroButton(state: WatchOpenState, onClick: () -> Unit) {
    val ringColor by animateColorAsState(
        targetValue = when (state) {
            WatchOpenState.Idle    -> WearColors.Text
            WatchOpenState.Sending -> WearColors.Accent
            WatchOpenState.Opened  -> WearColors.Accent
            WatchOpenState.Failed  -> WearColors.Danger
        },
        animationSpec = tween(200),
        label = "ringColor",
    )
    val fillColor by animateColorAsState(
        targetValue = when (state) {
            WatchOpenState.Opened -> WearColors.Accent
            WatchOpenState.Failed -> WearColors.Danger
            else                  -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "fillColor",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (state == WatchOpenState.Sending) {
            WatchExpandingPulses(baseRadiusDp = 60.dp, maxRadiusDp = 90.dp)
        }

        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(2.dp, ringColor, CircleShape)
                .clickable(enabled = state == WatchOpenState.Idle, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                WatchOpenState.Opened -> WatchCheckGlyph(color = Color.White, sizeDp = 48.dp)
                WatchOpenState.Failed -> WatchCrossGlyph(color = Color.White, sizeDp = 48.dp)
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WatchGMark(
                        size = 40.dp,
                        color = if (state == WatchOpenState.Sending) WearColors.Accent else WearColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state == WatchOpenState.Sending)
                            stringResource(R.string.watch_sending)
                        else
                            stringResource(R.string.watch_tap_to_open),
                        color = if (state == WatchOpenState.Sending) WearColors.Accent else WearColors.Text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchGMark(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val u = s / 64f
        val cx = s / 2f
        val cy = s / 2f
        val outerR = 24f * u
        val innerR = 14f * u

        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(Offset(cx - outerR, cy - outerR), Offset(cx + outerR, cy + outerR)))
            addOval(Rect(Offset(cx - innerR, cy - innerR), Offset(cx + innerR, cy + innerR)))
        }
        drawPath(ring, color = color)

        val tongueH = 8f * u
        val tongue = Path().apply {
            moveTo(cx, cy - tongueH / 2f)
            lineTo(cx + outerR + 2f * u, cy - tongueH / 2f)
            lineTo(cx + outerR + 2f * u, cy + tongueH / 2f)
            lineTo(cx, cy + tongueH / 2f)
            close()
        }
        drawPath(tongue, color = color)
    }
}

@Composable
private fun WatchExpandingPulses(baseRadiusDp: Dp, maxRadiusDp: Dp) {
    val transition = rememberInfiniteTransition(label = "pulses")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
        ),
        label = "t",
    )
    Canvas(modifier = Modifier.size(maxRadiusDp * 2)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseR = baseRadiusDp.toPx()
        val maxR = maxRadiusDp.toPx()
        val span = maxR - baseR
        for (i in 0..2) {
            val phase = ((t + i / 3f) % 1f)
            val r = baseR + span * phase
            val alpha = (1f - phase).coerceIn(0f, 1f) *
                        (if (phase < 0.08f) phase / 0.08f else 1f)
            drawCircle(
                color = WearColors.Accent.copy(alpha = 0.45f * alpha),
                radius = r,
                center = center,
                style = Stroke(width = 2f),
            )
        }
    }
}

@Composable
private fun WatchCheckGlyph(color: Color, sizeDp: Dp) {
    Canvas(modifier = Modifier.size(sizeDp)) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.21f, w * 0.52f)
            lineTo(w * 0.40f, w * 0.71f)
            lineTo(w * 0.79f, w * 0.30f)
        }
        drawPath(path, color = color,
            style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun WatchCrossGlyph(color: Color, sizeDp: Dp) {
    Canvas(modifier = Modifier.size(sizeDp)) {
        val w = size.width
        val style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path = Path().apply {
            moveTo(w * 0.28f, w * 0.28f); lineTo(w * 0.72f, w * 0.72f)
        }, color = color, style = style)
        drawPath(path = Path().apply {
            moveTo(w * 0.72f, w * 0.28f); lineTo(w * 0.28f, w * 0.72f)
        }, color = color, style = style)
    }
}

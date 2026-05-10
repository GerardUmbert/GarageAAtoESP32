package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ui.components.GMark
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

enum class OpenState { Idle, Sending, Opened, Failed }

/** Top-of-screen status pill — reflects whether the paired opener is in range. */
enum class PresenceStatus { InRange, OutOfRange, NotPaired }

@Composable
fun MainScreen(
    deviceLabel: String,
    state: OpenState,
    presence: PresenceStatus,
    lastOpenedLabel: String?,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(bottom = 24.dp),
    ) {
        // Status bar row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (dotColor, statusText) = when (presence) {
                PresenceStatus.InRange    -> GarageColors.Accent    to stringResource(R.string.status_in_range)
                PresenceStatus.OutOfRange -> GarageColors.TextFaint to stringResource(R.string.status_out_of_range)
                PresenceStatus.NotPaired  -> GarageColors.TextFaint to stringResource(R.string.status_not_paired)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = statusText,
                color = GarageColors.TextDim,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, GarageColors.HairlineStrong, CircleShape)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", color = GarageColors.Text, fontSize = 16.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.main_paired_opener),
                color = GarageColors.TextFaint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = deviceLabel,
                color = GarageColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )

            Spacer(Modifier.weight(1f))

            HeroButton(state = state, onClick = onOpen)

            Spacer(Modifier.weight(1f))

            LastOpenedRow(lastOpenedLabel = lastOpenedLabel)
        }
    }
}

@Composable
private fun HeroButton(state: OpenState, onClick: () -> Unit) {
    val ringColor by animateColorAsState(
        targetValue = when (state) {
            OpenState.Idle    -> GarageColors.Text
            OpenState.Sending -> GarageColors.Accent
            OpenState.Opened  -> GarageColors.Accent
            OpenState.Failed  -> GarageColors.DangerPastel
        },
        animationSpec = tween(200),
        label = "ringColor",
    )
    val fillColor by animateColorAsState(
        targetValue = when (state) {
            OpenState.Opened -> GarageColors.Accent
            OpenState.Failed -> GarageColors.DangerPastel
            else             -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "fillColor",
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Concentric expanding pulses (Sending state only).
        // Three pulses staggered by 1/3 of the loop so the ring receives
        // a continuous "wave" rather than three pulses arriving together.
        if (state == OpenState.Sending) {
            ExpandingPulses(baseRadiusDp = 120.dp, maxRadiusDp = 200.dp)
        }

        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(2.dp, ringColor, CircleShape)
                .clickable(enabled = state == OpenState.Idle, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                OpenState.Opened -> CheckGlyph(color = GarageColors.AccentDeep, sizeDp = 84.dp)
                OpenState.Failed -> CrossGlyph(color = GarageColors.DangerDeep, sizeDp = 84.dp)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GMark(
                        size = 72.dp,
                        color = if (state == OpenState.Sending) GarageColors.Accent else GarageColors.Text,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (state == OpenState.Sending) stringResource(R.string.main_sending) else stringResource(R.string.main_tap_to_open),
                        color = if (state == OpenState.Sending) GarageColors.Accent else GarageColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandingPulses(baseRadiusDp: Dp, maxRadiusDp: Dp) {
    val transition = rememberInfiniteTransition(label = "pulses")
    // One driver 0..1, three pulses read it offset by 0/0.33/0.66
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
            // Fade out as the ring expands; fade in slightly at the very start.
            val alpha = (1f - phase).coerceIn(0f, 1f) *
                        (if (phase < 0.08f) phase / 0.08f else 1f)
            drawCircle(
                color = GarageColors.Accent.copy(alpha = 0.45f * alpha),
                radius = r,
                center = center,
                style = Stroke(width = 2f),
            )
        }
    }
}

@Composable
private fun CheckGlyph(color: Color, sizeDp: Dp) {
    Canvas(modifier = Modifier.size(sizeDp)) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.21f, w * 0.52f)
            lineTo(w * 0.40f, w * 0.71f)
            lineTo(w * 0.79f, w * 0.30f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun CrossGlyph(color: Color, sizeDp: Dp) {
    Canvas(modifier = Modifier.size(sizeDp)) {
        val w = size.width
        val style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Top-left to bottom-right
        drawPath(
            path = Path().apply {
                moveTo(w * 0.28f, w * 0.28f)
                lineTo(w * 0.72f, w * 0.72f)
            },
            color = color,
            style = style,
        )
        // Top-right to bottom-left
        drawPath(
            path = Path().apply {
                moveTo(w * 0.72f, w * 0.28f)
                lineTo(w * 0.28f, w * 0.72f)
            },
            color = color,
            style = style,
        )
    }
}

@Composable
private fun LastOpenedRow(lastOpenedLabel: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GarageColors.Surface)
            .border(1.dp, GarageColors.Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.main_last_opened),
                color = GarageColors.TextDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = lastOpenedLabel ?: stringResource(R.string.main_never),
                color = GarageColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (lastOpenedLabel != null) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

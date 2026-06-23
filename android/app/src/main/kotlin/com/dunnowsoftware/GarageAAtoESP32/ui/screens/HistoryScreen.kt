package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryEntry
import com.dunnowsoftware.GarageAAtoESP32.data.OpenHistoryStore
import com.dunnowsoftware.GarageAAtoESP32.data.OpenOutcome
import com.dunnowsoftware.GarageAAtoESP32.data.TriggerSource
import com.dunnowsoftware.GarageAAtoESP32.ui.HAPTIC_TAP
import com.dunnowsoftware.GarageAAtoESP32.ui.vibrate
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    deviceAddress: String?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val entries = remember {
        if (deviceAddress != null)
            OpenHistoryStore.readByDevice(ctx, deviceAddress)
        else
            OpenHistoryStore.readAll(ctx)
    }
    var accordionMode by remember { mutableStateOf(true) }
    val expandedKey = remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(accordionMode) { expandedKey.value = null }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            TopBar(
                onBack = onBack,
                right = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, GarageColors.HairlineStrong, CircleShape)
                            .clickable {
                                vibrate(ctx, HAPTIC_TAP)
                                accordionMode = !accordionMode
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (accordionMode) Icons.Outlined.UnfoldLess else Icons.Outlined.SwapVert,
                            contentDescription = null,
                            tint = GarageColors.Text,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }

        item {
            Text(
                text = stringResource(R.string.history_title),
                color = GarageColors.Text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp),
            )
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.history_empty),
                    color = GarageColors.TextFaint,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            // Group by calendar day, emit a date separator before each new day
            val grouped = entries.groupBy { dayKey(it.timestampMs) }
            val today = dayKey(System.currentTimeMillis())
            val yesterday = dayKey(System.currentTimeMillis() - 86_400_000L)
            val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L

            val activeKey = if (accordionMode) expandedKey else null

            grouped.forEach { (dayKey, dayEntries) ->
                val firstTs = dayEntries.first().timestampMs
                val label = when (dayKey) {
                    today     -> ctx.getString(R.string.history_day_today)
                    yesterday -> ctx.getString(R.string.history_day_yesterday)
                    else -> {
                        if (firstTs >= weekAgo) {
                            val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(firstTs))
                            ctx.getString(R.string.history_day_last_week, dayName)
                        } else {
                            SimpleDateFormat("EEEE d", Locale.getDefault()).format(Date(firstTs))
                        }
                    }
                }
                stickyHeader(key = "sep_$dayKey") {
                    DateSeparator(label = label)
                }
                items(dayEntries, key = { it.timestampMs }) { entry ->
                    HistoryRow(
                        entry = entry,
                        expandedKey = activeKey,
                    )
                }
            }
        }
    }
}

private fun dayKey(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

@Composable
private fun DateSeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GarageColors.Bg)
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = GarageColors.TextFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun HistoryRow(entry: OpenHistoryEntry, expandedKey: MutableState<Long?>?) {
    var localExpanded by rememberSaveable(entry.timestampMs) { mutableStateOf(false) }
    val expanded = if (expandedKey != null) expandedKey.value == entry.timestampMs else localExpanded
    val onToggle: () -> Unit = if (expandedKey != null) {
        { expandedKey.value = if (expandedKey.value == entry.timestampMs) null else entry.timestampMs }
    } else {
        { localExpanded = !localExpanded }
    }

    val dotColor = when (entry.outcome) {
        OpenOutcome.SUCCESS    -> GarageColors.Accent
        OpenOutcome.FAILED_BLE -> GarageColors.Danger
        OpenOutcome.SUPPRESSED -> GarageColors.TextFaint
    }
    val outcomeText = when (entry.outcome) {
        OpenOutcome.SUCCESS    -> stringResource(R.string.history_outcome_success)
        OpenOutcome.FAILED_BLE -> stringResource(R.string.history_outcome_failed)
        OpenOutcome.SUPPRESSED -> stringResource(R.string.history_outcome_suppressed)
    }
    val triggerText = when (entry.trigger) {
        TriggerSource.MANUAL_PHONE  -> stringResource(R.string.history_trigger_manual_phone)
        TriggerSource.MANUAL_AA     -> stringResource(R.string.history_trigger_manual_aa)
        TriggerSource.AUTO_GEOFENCE -> stringResource(R.string.history_trigger_auto)
        TriggerSource.VOICE         -> stringResource(R.string.history_trigger_voice)
    }
    val timeStr = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestampMs))
    }
    val dateStr = remember(entry.timestampMs) {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(entry.timestampMs))
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )

    // Outer Box: Canvas uses matchParentSize so it always covers the full row height.
    // The inner Row drives the height; Canvas draws on top without affecting layout.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
    ) {
        val lineColor = GarageColors.Hairline
        // Dot centre: 16dp top padding + ~10dp (half of ~20dp line height for 14sp text)
        val dotOffsetDp = 31.dp
        Canvas(modifier = Modifier.width(32.dp).matchParentSize()) {
            val cx = 16.dp.toPx()
            val dotR = 5.dp.toPx()
            val dotY = dotOffsetDp.toPx()
            drawLine(lineColor, Offset(cx, 0f), Offset(cx, dotY - dotR), strokeWidth = 1.dp.toPx())
            drawLine(lineColor, Offset(cx, dotY + dotR), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(dotColor, radius = dotR, center = Offset(cx, dotY))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(32.dp))
            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp, bottom = 20.dp),
            ) {
                // Main row: time · trigger · outcome chip · animated chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = timeStr,
                        color = GarageColors.TextDim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = triggerText,
                        color = GarageColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "▾",
                        color = GarageColors.TextFaint,
                        fontSize = 12.sp,
                        modifier = Modifier.rotate(chevronRotation),
                    )
                    Spacer(Modifier.weight(1f))
                    OutcomeChip(text = outcomeText, color = dotColor)
                }

                // Expandable detail panel
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GarageColors.Surface)
                            .border(1.dp, GarageColors.Hairline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DetailLine(label = dateStr, value = null, valueColor = GarageColors.TextDim)
                        DetailLine(
                            label = stringResource(R.string.history_device_label, entry.deviceName),
                            value = entry.deviceAddress,
                            valueColor = GarageColors.TextFaint,
                        )
                        when (entry.outcome) {
                            OpenOutcome.FAILED_BLE -> {
                                val reason = if (entry.detail == "AUTH_FAILURE")
                                    stringResource(R.string.history_fail_auth)
                                else
                                    stringResource(R.string.history_fail_ble)
                                Text(text = reason, color = GarageColors.DangerPastel, fontSize = 12.sp)
                                if (entry.detail != null && entry.detail != "AUTH_FAILURE") {
                                    Text(
                                        text = formatGateDetail(entry.detail),
                                        color = GarageColors.TextDim,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            OpenOutcome.SUCCESS -> {
                                if (entry.detail != null) {
                                    Text(
                                        text = formatGateDetail(entry.detail),
                                        color = GarageColors.TextDim,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            OpenOutcome.SUPPRESSED -> {
                                if (entry.detail != null) {
                                    Text(
                                        text = formatSuppressedDetail(entry.detail),
                                        color = GarageColors.TextDim,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeChip(text: String, color: Color) {
    val bg = color.copy(alpha = 0.12f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun formatGateDetail(detail: String): String {
    val resId = when (detail) {
        "AA_CONNECTED"       -> R.string.history_gate_aa_connected
        "SPEED"              -> R.string.history_gate_speed
        "LAST_LOCATION_SPEED"-> R.string.history_gate_last_location_speed
        "IN_VEHICLE"         -> R.string.history_suppressed_activity_in_vehicle
        "ON_BICYCLE"         -> R.string.history_suppressed_activity_on_bicycle
        else                 -> null
    }
    return if (resId != null) stringResource(resId)
    else stringResource(R.string.history_detail_gate, detail)
}

@Composable
private fun formatSuppressedDetail(detail: String): String {
    val prefix = "SUPPRESSED_V2:"
    if (!detail.startsWith(prefix)) {
        // Legacy entries: show as-is under the old "Reason: …" label
        return stringResource(R.string.history_detail_suppressed, detail)
    }
    val parts = detail.removePrefix(prefix).split(":")
    if (parts.size < 3) return stringResource(R.string.history_detail_suppressed, detail)
    val activityKey = parts[0]
    val confidence = parts[1].toIntOrNull() ?: 0
    val speedKmh = parts[2].toFloatOrNull() ?: -1f
    val activityLabel = stringResource(when (activityKey) {
        "IN_VEHICLE"  -> R.string.history_suppressed_activity_in_vehicle
        "ON_FOOT"     -> R.string.history_suppressed_activity_on_foot
        "WALKING"     -> R.string.history_suppressed_activity_walking
        "RUNNING"     -> R.string.history_suppressed_activity_running
        "ON_BICYCLE"  -> R.string.history_suppressed_activity_on_bicycle
        "STILL"       -> R.string.history_suppressed_activity_still
        else          -> R.string.history_suppressed_activity_unknown
    })
    val speedStr = if (speedKmh >= 0f) "%.1f km/h".format(speedKmh) else "? km/h"
    return stringResource(R.string.history_suppressed_detail, activityLabel, confidence, speedStr)
}

@Composable
private fun DetailLine(label: String, value: String?, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = GarageColors.TextDim, fontSize = 12.sp)
        if (value != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

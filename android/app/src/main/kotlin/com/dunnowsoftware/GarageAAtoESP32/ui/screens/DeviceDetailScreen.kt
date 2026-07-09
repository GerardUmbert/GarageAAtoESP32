package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
import com.dunnowsoftware.GarageAAtoESP32.ui.HAPTIC_TAP
import com.dunnowsoftware.GarageAAtoESP32.ui.vibrate
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

/**
 * Per-device settings, reached from the multi-device list in [SettingsScreen]. Mirrors
 * the fields the single-device hero card + global Auto-open section used to show inline
 * before multiple devices existed — same content, just scoped to one [DeviceSummary] and
 * given its own screen instead of living inline in Settings.
 */
@Composable
fun DeviceDetailScreen(
    device: DeviceSummary,
    presence: PresenceStatus = PresenceStatus.OutOfRange,
    geofenceSet: Boolean = false,
    geofenceEnabled: Boolean = false,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onRepair: () -> Unit,
    onRemove: () -> Unit,
    onEditWebhook: () -> Unit,
    onConnectToAp: () -> Unit,
    onGeofencePicker: () -> Unit,
    onToggleGeofence: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { TopBar(onBack = onBack) }

        item {
            Text(
                text = device.name.ifEmpty { stringResource(R.string.settings_device_default_name) },
                color = GarageColors.Text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp),
            )
        }

        item {
            if (device.transport == TransportType.WEBHOOK) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(GarageColors.Surface)
                        .border(1.dp, GarageColors.Hairline, RoundedCornerShape(22.dp))
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Wifi,
                            contentDescription = null,
                            tint = GarageColors.Accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_webhook_badge),
                            color = GarageColors.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = device.webhookUrl.orEmpty(),
                        color = GarageColors.TextDim,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(16.dp))
                    SecondaryActionD(
                        text = stringResource(R.string.settings_webhook_edit_button),
                        onClick = onEditWebhook,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(GarageColors.Surface)
                        .border(1.dp, GarageColors.Hairline, RoundedCornerShape(22.dp))
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = if (presence == PresenceStatus.InRange) GarageColors.Accent else GarageColors.TextFaint
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                        Spacer(Modifier.width(8.dp))
                        val badgeText = stringResource(R.string.settings_paired_badge) +
                            if (presence == PresenceStatus.InRange) " · " + stringResource(R.string.status_in_range)
                            else ""
                        Text(
                            text = badgeText,
                            color = if (presence == PresenceStatus.InRange) GarageColors.Accent else GarageColors.TextDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.bleAddress.orEmpty(),
                            color = GarageColors.TextDim,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        if (device.bleHasWebLog) {
                            val ctxWifi = LocalContext.current
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GarageColors.Surface2)
                                    .clickable {
                                        vibrate(ctxWifi, HAPTIC_TAP)
                                        onConnectToAp()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = GarageColors.Accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryActionD(
                            text = stringResource(R.string.settings_repairbutton),
                            onClick = onRepair,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryActionD(
                            text = stringResource(R.string.settings_password_button),
                            onClick = onChangePassword,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionHeaderD(stringResource(R.string.settings_autoopen_header))
            CardD {
                val ctxGeo = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vibrate(ctxGeo, HAPTIC_TAP)
                            onGeofencePicker()
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_geofence_location_label),
                        color = GarageColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (geofenceSet) stringResource(R.string.settings_geofence_location_set)
                               else stringResource(R.string.settings_geofence_location_not_set),
                        color = if (geofenceSet) GarageColors.Accent else GarageColors.TextDim,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "›",
                        color = GarageColors.TextFaint,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 18.dp)
                        .background(GarageColors.Hairline),
                )
                ToggleRowD(
                    label = stringResource(R.string.settings_geofence_toggle_label),
                    description = if (geofenceSet) stringResource(R.string.settings_geofence_toggle_desc_active)
                                  else stringResource(R.string.settings_geofence_toggle_desc_no_location),
                    checked = geofenceEnabled,
                    onCheckedChange = if (geofenceSet) onToggleGeofence else { _ -> },
                    enabled = geofenceSet,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            SecondaryActionD(
                text = stringResource(R.string.device_detail_remove),
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth(),
                danger = true,
            )
        }
    }
}

@Composable
private fun SectionHeaderD(title: String) {
    Text(
        text = title.uppercase(),
        color = GarageColors.TextFaint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
    )
}

@Composable
private fun CardD(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GarageColors.Surface)
            .border(1.dp, GarageColors.Hairline, RoundedCornerShape(18.dp)),
    ) {
        content()
    }
}

@Composable
private fun ToggleRowD(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (enabled) GarageColors.Text else GarageColors.TextFaint,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(description, color = GarageColors.TextDim, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                vibrate(ctx, HAPTIC_TAP)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = GarageColors.AccentDeep,
                checkedTrackColor = GarageColors.Accent,
                uncheckedThumbColor = GarageColors.TextDim,
                uncheckedTrackColor = GarageColors.Surface2,
                uncheckedBorderColor = GarageColors.HairlineStrong,
                disabledCheckedThumbColor = GarageColors.TextFaint,
                disabledCheckedTrackColor = GarageColors.Surface2,
                disabledUncheckedThumbColor = GarageColors.TextFaint,
                disabledUncheckedTrackColor = GarageColors.Surface2,
                disabledUncheckedBorderColor = GarageColors.Hairline,
            ),
        )
    }
}

@Composable
private fun SecondaryActionD(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val ctx = LocalContext.current
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (danger) GarageColors.Danger else GarageColors.HairlineStrong, RoundedCornerShape(12.dp))
            .clickable {
                vibrate(ctx, HAPTIC_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (danger) GarageColors.Danger else GarageColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

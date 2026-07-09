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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.data.TransportType
import com.dunnowsoftware.GarageAAtoESP32.ui.HAPTIC_TAP
import com.dunnowsoftware.GarageAAtoESP32.ui.vibrate
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors
import com.dunnowsoftware.GarageAAtoESP32.ui.screens.PresenceStatus

internal data class LangOption(val tag: String?, val nativeName: String)

internal val supportedLanguages = listOf(
    LangOption(null,    ""),              // always first; label resolved at runtime
    LangOption("ca",    "Català"),
    LangOption("de",    "Deutsch"),
    LangOption("en",    "English"),
    LangOption("es",    "Español"),
    LangOption("fr",    "Français"),
    LangOption("it",    "Italiano"),
    LangOption("pt-PT", "Português (Portugal)"),
    LangOption("fi",    "Suomi"),
)

/** Lightweight, UI-facing view of a GarageDevice — SettingsScreen doesn't need the full data model. */
data class DeviceSummary(
    val id: String,
    val name: String,
    val transport: TransportType,
    val bleAddress: String? = null,
    val bleHasWebLog: Boolean = false,
    val webhookUrl: String? = null,
)

@Composable
fun SettingsScreen(
    devices: List<DeviceSummary>,
    demoMode: Boolean,
    currentLocaleTag: String?,
    presence: PresenceStatus = PresenceStatus.OutOfRange,
    /** BLE address -> in-range, for the multi-device list. Webhook devices are absent (no presence concept). */
    blePresence: Map<String, Boolean> = emptyMap(),
    geofenceSet: Boolean = false,
    geofenceEnabled: Boolean = false,
    onBack: () -> Unit,
    onChangePassword: (deviceId: String) -> Unit,
    onRepair: () -> Unit,
    onUnpair: (deviceId: String) -> Unit,
    onRemoveWebhook: (deviceId: String) -> Unit = {},
    onEditWebhook: (deviceId: String) -> Unit = {},
    onPairAnother: () -> Unit,
    onToggleDemo: (Boolean) -> Unit,
    onLanguageScreen: () -> Unit,
    onGeofencePicker: () -> Unit = {},
    onToggleGeofence: (Boolean) -> Unit = {},
    onShareLog: () -> Unit = {},
    onConnectToAp: (deviceId: String) -> Unit = {},
    onDeviceDetail: (deviceId: String) -> Unit = {},
) {
    val single = devices.singleOrNull()

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
                text = stringResource(R.string.settings_title),
                color = GarageColors.Text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp),
            )
        }

        if (devices.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(GarageColors.Surface)
                        .border(1.dp, GarageColors.Hairline, RoundedCornerShape(22.dp))
                        .padding(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_no_device_title),
                        color = GarageColors.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_no_device_subtitle),
                        color = GarageColors.TextDim,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    SecondaryAction(
                        text = stringResource(R.string.settings_pair_opener),
                        onClick = onRepair,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        } else if (single != null) {
            // Single-device layout: unchanged from the pre-multi-device UI — hero card
            // with inline actions, no device list, no "Selected opener" dropdown.
            item {
                DeviceHeroCard(
                    device = single,
                    presence = presence,
                    onUnpair = { onUnpair(single.id) },
                    onRemoveWebhook = { onRemoveWebhook(single.id) },
                    onEditWebhook = { onEditWebhook(single.id) },
                    onRepair = onRepair,
                    onChangePassword = { onChangePassword(single.id) },
                    onConnectToAp = { onConnectToAp(single.id) },
                )
                Spacer(Modifier.height(12.dp))
                SecondaryAction(
                    text = stringResource(R.string.settings_pair_another),
                    onClick = onPairAnother,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(28.dp))
            }
        } else {
            // Multi-device layout: device list replaces the hero card; per-device
            // settings (geofence, password, etc.) move to Device Detail. The
            // "Selected opener" picker itself lives on the main screen, not here.
            item {
                SectionHeader(stringResource(R.string.settings_devices_header))
                Card {
                    devices.forEachIndexed { index, device ->
                        DeviceRow(
                            device = device,
                            inRange = device.bleAddress?.let { blePresence[it] },
                            onClick = { onDeviceDetail(device.id) },
                        )
                        if (index != devices.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(horizontal = 18.dp)
                                    .background(GarageColors.Hairline),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SecondaryAction(
                    text = stringResource(R.string.settings_pair_another),
                    onClick = onPairAnother,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        item {
            SectionHeader(stringResource(R.string.settings_language_header))
            Card {
                val currentLangLabel = resolvedLanguageLabel(currentLocaleTag)
                val ctxLang = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vibrate(ctxLang, HAPTIC_TAP)
                            onLanguageScreen()
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_language_header),
                        color = GarageColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = currentLangLabel,
                        color = GarageColors.TextDim,
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
            }
            Spacer(Modifier.height(24.dp))
        }

        // AUTO-OPEN section: only shown here for the single-device case. With 2+
        // devices this moves into each device's Device Detail screen instead, since
        // a single global location picker is ambiguous once there's more than one
        // garage.
        if (single != null) {
            item {
                SectionHeader(stringResource(R.string.settings_autoopen_header))
                Card {
                    // Location row
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

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 18.dp)
                            .background(GarageColors.Hairline),
                    )

                    // Auto-open toggle — only enabled when a geofence is set
                    ToggleRow(
                        label = stringResource(R.string.settings_geofence_toggle_label),
                        description = if (geofenceSet) stringResource(R.string.settings_geofence_toggle_desc_active)
                                      else stringResource(R.string.settings_geofence_toggle_desc_no_location),
                        checked = geofenceEnabled,
                        onCheckedChange = if (geofenceSet) onToggleGeofence else { _ -> },
                        enabled = geofenceSet,
                    )

                }
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            SectionHeader(stringResource(R.string.settings_testing_header))
            Card {
                ToggleRow(
                    label = stringResource(R.string.settings_demo_label),
                    description = stringResource(R.string.settings_demo_description),
                    checked = demoMode,
                    onCheckedChange = onToggleDemo,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            SectionHeader(stringResource(R.string.settings_diagnostics_header))
            Card {
                val ctxLog = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vibrate(ctxLog, HAPTIC_TAP)
                            onShareLog()
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_share_log_label),
                        color = GarageColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "›",
                        color = GarageColors.TextFaint,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }

        item {
            val ctx = LocalContext.current
            val versionName = remember {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            }
            Text(
                text = stringResource(R.string.settings_version_label) + " " + (versionName ?: "—"),
                color = GarageColors.TextFaint,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeviceHeroCard(
    device: DeviceSummary,
    presence: PresenceStatus,
    onUnpair: () -> Unit,
    onRemoveWebhook: () -> Unit,
    onEditWebhook: () -> Unit,
    onRepair: () -> Unit,
    onChangePassword: () -> Unit,
    onConnectToAp: () -> Unit,
) {
    if (device.transport == TransportType.WEBHOOK) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                        modifier = Modifier.weight(1f),
                    )
                    val ctxRemove = LocalContext.current
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GarageColors.DangerSoft)
                            .clickable {
                                vibrate(ctxRemove, HAPTIC_TAP)
                                onRemoveWebhook()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "×",
                            color = GarageColors.Danger,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = device.name.ifEmpty { stringResource(R.string.settings_device_default_name) },
                    color = GarageColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = maskUrl(device.webhookUrl.orEmpty()),
                    color = GarageColors.TextDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryAction(
                    text = stringResource(R.string.settings_webhook_edit_button),
                    onClick = onEditWebhook,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                        modifier = Modifier.weight(1f),
                    )
                    val ctxUnpair = LocalContext.current
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GarageColors.DangerSoft)
                            .clickable {
                                vibrate(ctxUnpair, HAPTIC_TAP)
                                onUnpair()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "×",
                            color = GarageColors.Danger,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = device.name.ifEmpty { stringResource(R.string.settings_device_default_name) },
                    color = GarageColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
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
                    SecondaryAction(
                        text = stringResource(R.string.settings_repairbutton),
                        onClick = onRepair,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryAction(
                        text = stringResource(R.string.settings_password_button),
                        onClick = onChangePassword,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceSummary, inRange: Boolean?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                vibrate(ctx, HAPTIC_TAP)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (device.transport == TransportType.BLE) {
            // Real presence dot — green when this device's BLE advertisement was
            // seen recently, grey otherwise.
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (inRange == true) GarageColors.Accent else GarageColors.TextFaint),
            )
        } else {
            // Webhooks have no proximity/health signal to check — assumed reachable
            // by default rather than shown as a false "not connected" state.
            Icon(
                imageVector = Icons.Outlined.Wifi,
                contentDescription = null,
                tint = GarageColors.Accent,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name.ifEmpty { stringResource(R.string.settings_device_default_name) },
                color = GarageColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (device.transport == TransportType.WEBHOOK)
                    stringResource(R.string.settings_webhook_badge)
                else if (inRange == true)
                    stringResource(R.string.settings_paired_badge) + " · " + stringResource(R.string.status_in_range)
                else
                    stringResource(R.string.settings_paired_badge),
                color = GarageColors.TextDim,
                fontSize = 12.sp,
            )
        }
        Text(
            text = "›",
            color = GarageColors.TextFaint,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
        )
    }
}


/**
 * Truncates a webhook URL for display. The URL itself may be the sensitive
 * value (e.g. an unguessable HA webhook_id embedded in the path), so it's
 * masked similarly to how a token would be — scheme+host visible, path
 * collapsed.
 */
private fun maskUrl(url: String): String {
    return try {
        val u = java.net.URI(url)
        val host = u.host ?: return url.take(28) + if (url.length > 28) "…" else ""
        val scheme = u.scheme ?: "http"
        "$scheme://$host/…"
    } catch (_: Throwable) {
        url.take(28) + if (url.length > 28) "…" else ""
    }
}

@Composable
private fun resolvedLanguageLabel(tag: String?): String {
    if (tag == null) return stringResource(R.string.settings_language_system)
    return supportedLanguages.find { it.tag == tag }?.nativeName ?: tag
}

@Composable
private fun SectionHeader(title: String) {
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
private fun Card(content: @Composable () -> Unit) {
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
private fun ToggleRow(
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
private fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GarageColors.HairlineStrong, RoundedCornerShape(12.dp))
            .clickable {
                vibrate(ctx, HAPTIC_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = GarageColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Suppress("unused")
private val ignored: Color = Color.Transparent

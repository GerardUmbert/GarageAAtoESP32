package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

@Composable
fun SettingsScreen(
    deviceName: String?,
    deviceAddress: String?,
    demoMode: Boolean,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onRepair: () -> Unit,
    onUnpair: () -> Unit,
    onToggleDemo: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        TopBar(onBack = onBack)

        Text(
            text = "Settings",
            color = GarageColors.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp),
        )

        // Paired hero card
        if (deviceAddress != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(GarageColors.Surface)
                    .border(1.dp, GarageColors.Hairline, RoundedCornerShape(22.dp))
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GarageColors.Accent),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "PAIRED",
                        color = GarageColors.TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = deviceName ?: "ESP32 Garage",
                    color = GarageColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
                Text(
                    text = deviceAddress,
                    color = GarageColors.TextDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    SecondaryAction(
                        text = "Re-pair",
                        onClick = onRepair,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(GarageColors.Surface)
                    .border(1.dp, GarageColors.Hairline, RoundedCornerShape(22.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "No opener paired",
                    color = GarageColors.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pair an ESP32 to get started.",
                    color = GarageColors.TextDim,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryAction(text = "Pair an opener", onClick = onRepair, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(28.dp))
        }

        SectionHeader("Security")
        Card {
            SettingsRow(
                label = "Change password",
                value = if (true) "•••••" else "",
                onClick = onChangePassword,
            )
        }

        Spacer(Modifier.height(24.dp))

        SectionHeader("Testing")
        Card {
            ToggleRow(
                label = "Demo mode",
                description = "Simulate open without an ESP32",
                checked = demoMode,
                onCheckedChange = onToggleDemo,
            )
        }

        Spacer(Modifier.height(24.dp))

        if (deviceAddress != null) {
            SectionHeader("Danger zone")
            Card {
                SettingsRow(
                    label = "Unpair this opener",
                    value = "",
                    onClick = onUnpair,
                    danger = true,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "GarageAA",
            color = GarageColors.TextFaint,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
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
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val color = if (danger) GarageColors.Danger else GarageColors.Text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(text = value, color = GarageColors.TextDim, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
        }
        if (!danger) {
            Text(text = "›", color = GarageColors.TextDim, fontSize = 22.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = GarageColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = GarageColors.TextDim, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GarageColors.AccentDeep,
                checkedTrackColor = GarageColors.Accent,
                uncheckedThumbColor = GarageColors.TextDim,
                uncheckedTrackColor = GarageColors.Surface2,
                uncheckedBorderColor = GarageColors.HairlineStrong,
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
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GarageColors.HairlineStrong, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = GarageColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Suppress("unused")
private val ignored: Color = Color.Transparent

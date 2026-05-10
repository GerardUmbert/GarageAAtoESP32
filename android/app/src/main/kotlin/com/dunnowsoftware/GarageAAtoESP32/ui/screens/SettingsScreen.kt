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
import androidx.compose.runtime.remember
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
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

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

@Composable
fun SettingsScreen(
    deviceName: String?,
    deviceAddress: String?,
    demoMode: Boolean,
    currentLocaleTag: String?,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onRepair: () -> Unit,
    onUnpair: () -> Unit,
    onPairAnother: () -> Unit,
    onToggleDemo: (Boolean) -> Unit,
    onLanguageScreen: () -> Unit,
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
            text = stringResource(R.string.settings_title),
            color = GarageColors.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp),
        )

        // Paired hero card
        if (deviceAddress != null) {
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
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(GarageColors.Accent),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_paired_badge),
                            color = GarageColors.TextDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = deviceName ?: stringResource(R.string.settings_device_default_name),
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
                // Unpair icon button — top-right corner of the card
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GarageColors.DangerSoft)
                        .clickable(onClick = onUnpair),
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
            Spacer(Modifier.height(12.dp))
            SecondaryAction(
                text = stringResource(R.string.settings_pair_another),
                onClick = onPairAnother,
                modifier = Modifier.fillMaxWidth(),
            )
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

        SectionHeader(stringResource(R.string.settings_language_header))
        Card {
            val currentLangLabel = resolvedLanguageLabel(currentLocaleTag)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLanguageScreen)
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

        SectionHeader(stringResource(R.string.settings_testing_header))
        Card {
            ToggleRow(
                label = stringResource(R.string.settings_demo_label),
                description = stringResource(R.string.settings_demo_description),
                checked = demoMode,
                onCheckedChange = onToggleDemo,
            )
        }

        Spacer(Modifier.weight(1f))

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

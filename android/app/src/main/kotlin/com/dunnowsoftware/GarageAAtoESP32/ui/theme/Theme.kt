package com.dunnowsoftware.GarageAAtoESP32.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object GarageColors {
    val Bg              = Color(0xFF0A0C0E)
    val Surface         = Color(0xFF14181C)
    val Surface2        = Color(0xFF1B2025)
    val Hairline        = Color(0x0FFFFFFF)
    val HairlineStrong  = Color(0x1AFFFFFF)
    val Text            = Color(0xFFF3F5F7)
    val TextDim         = Color(0xFF8A939C)
    val TextFaint       = Color(0xFF5A6169)
    val Accent          = Color(0xFF2AD4A3)
    val AccentSoft      = Color(0x1F2AD4A3)
    val AccentLine      = Color(0x4D2AD4A3)
    val AccentDeep      = Color(0xFF08231B)
    val Danger          = Color(0xFFFF5D6C)
    // Pastel-red variants for failure state — same role as Accent/AccentDeep
    val DangerPastel    = Color(0xFFFF8A95)
    val DangerSoft      = Color(0x1FFF8A95)
    val DangerLine      = Color(0x4DFF8A95)
    val DangerDeep      = Color(0xFF3A0F13)
}

private val GarageDarkScheme = darkColorScheme(
    background     = GarageColors.Bg,
    surface        = GarageColors.Surface,
    primary        = GarageColors.Accent,
    onPrimary      = GarageColors.AccentDeep,
    onBackground   = GarageColors.Text,
    onSurface      = GarageColors.Text,
    error          = GarageColors.Danger,
)

@Composable
fun GarageTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GarageDarkScheme,
        typography = GarageTypography,
        content = content,
    )
}

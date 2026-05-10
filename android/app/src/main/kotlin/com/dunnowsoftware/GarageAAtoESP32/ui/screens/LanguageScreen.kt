package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

@Composable
fun LanguageScreen(
    currentLocaleTag: String?,
    onLanguageChange: (String?) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        TopBar(onBack = onBack, parentHorizontalPadding = 24.dp)

        Text(
            text = stringResource(R.string.settings_language_header),
            color = GarageColors.Text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(GarageColors.Surface)
                .border(1.dp, GarageColors.Hairline, RoundedCornerShape(18.dp)),
        ) {
            supportedLanguages.forEachIndexed { idx, lang ->
                val isSelected = lang.tag == currentLocaleTag
                val label = if (lang.tag == null) stringResource(R.string.settings_language_system) else lang.nativeName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLanguageChange(lang.tag) }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) GarageColors.Accent else GarageColors.Text,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(GarageColors.Accent),
                        )
                    }
                }
                if (idx != supportedLanguages.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .height(1.dp)
                            .background(GarageColors.Hairline),
                    )
                }
            }
        }
    }
}

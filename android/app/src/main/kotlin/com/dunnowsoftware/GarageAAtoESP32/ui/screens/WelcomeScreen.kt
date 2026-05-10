package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ui.components.GMark
import com.dunnowsoftware.GarageAAtoESP32.ui.components.PrimaryButton
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(GarageColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            GMark(size = 88.dp, color = GarageColors.AccentDeep)
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(R.string.welcome_title),
            color = GarageColors.Text,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            color = GarageColors.TextDim,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(text = stringResource(R.string.welcome_get_started), onClick = onGetStarted)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.welcome_no_account),
            color = GarageColors.TextDim,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

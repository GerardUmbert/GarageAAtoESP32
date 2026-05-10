package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ble.FoundDevice
import com.dunnowsoftware.GarageAAtoESP32.ui.components.GhostButton
import com.dunnowsoftware.GarageAAtoESP32.ui.components.PrimaryButton
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

@Composable
fun PairScreen(
    device: FoundDevice,
    onPair: (password: String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp),
    ) {
        TopBar(onBack = onCancel, parentHorizontalPadding = 32.dp)

        Text(
            text = stringResource(R.string.pair_title),
            color = GarageColors.Text,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.pair_subtitle),
            color = GarageColors.TextDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(28.dp))

        DeviceCard(device = device)

        Spacer(Modifier.height(20.dp))

        PasswordCard(
            password = password,
            onPasswordChange = { password = it },
            showPassword = showPassword,
            focusRequester = focusRequester,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (showPassword) stringResource(R.string.pair_hide_password) else stringResource(R.string.pair_show_password),
            color = GarageColors.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showPassword = !showPassword }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = stringResource(R.string.pair_confirm),
            onClick = { onPair(password.trim()) },
            enabled = password.trim().isNotEmpty(),
        )
        Spacer(Modifier.height(8.dp))
        GhostButton(text = stringResource(R.string.pair_cancel), onClick = onCancel)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun DeviceCard(device: FoundDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GarageColors.Surface)
            .border(1.dp, GarageColors.Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GarageColors.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chip),
                contentDescription = null,
                tint = GarageColors.Text,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                color = GarageColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = device.address,
                color = GarageColors.TextDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        SignalPill(rssi = device.rssi)
    }
}

@Composable
private fun SignalPill(rssi: Int) {
    val label = when {
        rssi >= -55 -> "strong"
        rssi >= -75 -> "fair"
        else        -> "weak"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(GarageColors.AccentSoft)
            .border(1.dp, GarageColors.AccentLine, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = GarageColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PasswordCard(
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    focusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GarageColors.Surface)
            .border(1.dp, GarageColors.Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.pair_password_label),
            color = GarageColors.TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        val hint = stringResource(R.string.pair_password_hint)
        BasicTextField(
            value = password,
            onValueChange = onPasswordChange,
            singleLine = true,
            textStyle = TextStyle(
                color = GarageColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(GarageColors.Accent),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                if (password.isEmpty()) {
                    Text(hint, color = GarageColors.TextFaint, fontSize = 18.sp)
                }
                inner()
            },
        )
    }
}

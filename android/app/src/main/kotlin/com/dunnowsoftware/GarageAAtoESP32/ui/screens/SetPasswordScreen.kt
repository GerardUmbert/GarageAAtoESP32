package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ui.HAPTIC_TAP
import com.dunnowsoftware.GarageAAtoESP32.ui.vibrate
import com.dunnowsoftware.GarageAAtoESP32.ui.components.GhostButton
import com.dunnowsoftware.GarageAAtoESP32.ui.components.PrimaryButton
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

@Composable
fun SetPasswordScreen(
    initialPassword: String,
    onSave: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    title: String = "",
    description: String = "",
    saveLabel: String = "",
) {
    val resolvedTitle = title.ifEmpty { stringResource(R.string.password_screen_set_title) }
    val resolvedDescription = description.ifEmpty { stringResource(R.string.password_screen_set_description) }
    val resolvedSaveLabel = saveLabel.ifEmpty { stringResource(R.string.password_screen_save) }

    var password by rememberSaveable { mutableStateOf(initialPassword) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp),
    ) {
        TopBar(onBack = onBack, parentHorizontalPadding = 32.dp)

        Text(
            text = resolvedTitle,
            color = GarageColors.Text,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = resolvedDescription,
            color = GarageColors.TextDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(48.dp))

        val hint = stringResource(R.string.password_screen_hint)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GarageColors.Surface)
                .border(1.dp, GarageColors.Hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            BasicTextField(
                value = password,
                onValueChange = { password = it },
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

        if (password.isNotEmpty() && password.trim().length < 8) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.password_screen_too_short),
                color = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        val ctxPwd = LocalContext.current
        Text(
            text = if (showPassword) stringResource(R.string.password_screen_hide) else stringResource(R.string.password_screen_show),
            color = GarageColors.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    vibrate(ctxPwd, HAPTIC_TAP)
                    showPassword = !showPassword
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = resolvedSaveLabel,
            onClick = { onSave(password.trim()) },
            enabled = password.trim().length >= 8,
        )
        if (onBack != null) {
            Spacer(Modifier.height(8.dp))
            GhostButton(text = stringResource(R.string.password_screen_cancel), onClick = onBack)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
internal fun TopBar(
    onBack: (() -> Unit)?,
    parentHorizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    right: (@Composable () -> Unit)? = null,
) {
    val chevronOffset = 16.dp - parentHorizontalPadding

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val ctx = LocalContext.current
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = chevronOffset)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        vibrate(ctx, HAPTIC_TAP)
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = GarageColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }
        Spacer(Modifier.weight(1f))
        right?.invoke()
    }
}

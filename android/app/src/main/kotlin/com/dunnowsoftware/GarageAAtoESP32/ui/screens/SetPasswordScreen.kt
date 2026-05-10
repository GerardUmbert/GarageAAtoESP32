package com.dunnowsoftware.GarageAAtoESP32.ui.screens

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dunnowsoftware.GarageAAtoESP32.ui.components.GhostButton
import com.dunnowsoftware.GarageAAtoESP32.ui.components.PrimaryButton
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

@Composable
fun SetPasswordScreen(
    initialPassword: String,
    onSave: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
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
            text = "Set a password",
            color = GarageColors.Text,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Required to open the door from your phone or car. Stored only on this device. Must match the password configured in the ESP32 firmware.",
            color = GarageColors.TextDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(48.dp))

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
                        Text("Enter password", color = GarageColors.TextFaint, fontSize = 18.sp)
                    }
                    inner()
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (showPassword) "Hide password" else "Show password",
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
            text = "Save",
            onClick = { onSave(password.trim()) },
            enabled = password.trim().isNotEmpty(),
        )
        if (onBack != null) {
            Spacer(Modifier.height(8.dp))
            GhostButton(text = "Cancel", onClick = onBack)
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
    // Anchor the chevron's hit-area box to 16dp from the actual screen edge
    // regardless of how much horizontal padding the caller's column applies.
    // The 40dp box's left edge sits at `parentPadding + offset` from the
    // screen edge, so offset = 16 - parentPadding.
    val chevronOffset = 16.dp - parentHorizontalPadding

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .offset(x = chevronOffset)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onBack),
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

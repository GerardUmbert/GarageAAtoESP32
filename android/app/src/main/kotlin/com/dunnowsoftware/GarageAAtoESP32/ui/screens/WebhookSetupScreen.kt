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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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

/**
 * Onboarding / settings screen for webhook transport — an alternative to
 * BLE pairing where "open" means an HTTP POST to a user-configured URL
 * (e.g. a Home Assistant native webhook trigger). No discovery step (unlike
 * BLE scan), no connectivity test — saving is sufficient, the first real
 * open attempt confirms it works.
 */
@Composable
fun WebhookSetupScreen(
    initialName: String = "",
    initialUrl: String = "",
    initialToken: String = "",
    onSave: (name: String, url: String, token: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var token by rememberSaveable { mutableStateOf(initialToken) }
    var showAdvanced by rememberSaveable { mutableStateOf(initialToken.isNotEmpty()) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val urlValid = url.trim().let { it.startsWith("http://") || it.startsWith("https://") }
    val canSave = name.trim().isNotEmpty() && urlValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp),
    ) {
        TopBar(onBack = onCancel, parentHorizontalPadding = 32.dp)

        Text(
            text = stringResource(R.string.webhook_setup_title),
            color = GarageColors.Text,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.webhook_setup_subtitle),
            color = GarageColors.TextDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(28.dp))

        FieldCard(
            label = stringResource(R.string.webhook_setup_name_label),
            value = name,
            onValueChange = { name = it },
            hint = stringResource(R.string.webhook_setup_name_hint),
            focusRequester = focusRequester,
            imeAction = ImeAction.Next,
        )

        Spacer(Modifier.height(12.dp))

        FieldCard(
            label = stringResource(R.string.webhook_setup_url_label),
            value = url,
            onValueChange = { url = it },
            hint = stringResource(R.string.webhook_setup_url_hint),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            singleLine = false,
        )

        Spacer(Modifier.height(16.dp))

        val ctx = LocalContext.current
        Text(
            text = if (showAdvanced) stringResource(R.string.webhook_setup_advanced_hide)
                   else stringResource(R.string.webhook_setup_advanced_show),
            color = GarageColors.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    vibrate(ctx, HAPTIC_TAP)
                    showAdvanced = !showAdvanced
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        if (showAdvanced) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GarageColors.Surface)
                    .border(1.dp, GarageColors.Hairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    text = stringResource(R.string.webhook_setup_token_label),
                    color = GarageColors.TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                val tokenHint = stringResource(R.string.webhook_setup_token_hint)
                BasicTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = GarageColors.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(GarageColors.Accent),
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (token.isEmpty()) {
                            Text(tokenHint, color = GarageColors.TextFaint, fontSize = 18.sp)
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (showToken) stringResource(R.string.pair_hide_password) else stringResource(R.string.pair_show_password),
                    color = GarageColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            vibrate(ctx, HAPTIC_TAP)
                            showToken = !showToken
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.webhook_setup_token_description),
                color = GarageColors.TextFaint,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.webhook_setup_security_note),
            color = GarageColors.TextFaint,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = stringResource(R.string.webhook_setup_save),
            onClick = { onSave(name.trim(), url.trim(), token.trim().takeIf { it.isNotEmpty() }) },
            enabled = canSave,
        )
        Spacer(Modifier.height(8.dp))
        GhostButton(text = stringResource(R.string.pair_cancel), onClick = onCancel)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun FieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    focusRequester: FocusRequester? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
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
            text = label,
            color = GarageColors.TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        var fieldModifier = Modifier.fillMaxWidth()
        if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = GarageColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(GarageColors.Accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
            ),
            modifier = fieldModifier,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(hint, color = GarageColors.TextFaint, fontSize = 18.sp)
                }
                inner()
            },
        )
    }
}

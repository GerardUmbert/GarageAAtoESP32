package com.dunnowsoftware.GarageAAtoESP32.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.dunnowsoftware.GarageAAtoESP32.R
import com.dunnowsoftware.GarageAAtoESP32.ui.components.GhostButton
import com.dunnowsoftware.GarageAAtoESP32.ui.components.PrimaryButton
import com.dunnowsoftware.GarageAAtoESP32.ui.theme.GarageColors

enum class OnboardingStepId {
    FINE_LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATIONS,
    BATTERY,
    UNUSED_APP,
}

data class OnboardingStep(
    val id: OnboardingStepId,
    val titleRes: Int,
    val bodyRes: Int,
    val illustration: @Composable () -> Unit,
)

@Composable
fun GeofencePermissionOnboardingScreen(
    steps: List<OnboardingStep>,
    onStepAction: (OnboardingStepId) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    cancelLabel: String = stringResource(R.string.onboarding_skip),
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps.getOrNull(index) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GarageColors.Bg)
            .padding(horizontal = 24.dp),
    ) {
        TopBar(onBack = onCancel)

        Text(
            text = stringResource(R.string.onboarding_step_indicator, index + 1, steps.size),
            color = GarageColors.TextFaint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        Text(
            text = stringResource(step.titleRes),
            color = GarageColors.Text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Text(
            text = stringResource(step.bodyRes),
            color = GarageColors.TextDim,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 28.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            step.illustration()
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = {
                if (index < steps.lastIndex) {
                    onStepAction(step.id)
                    index++
                } else {
                    onStepAction(step.id)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        GhostButton(
            text = cancelLabel,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Phone-frame mock-up shell
// ---------------------------------------------------------------------------

@Composable
private fun PhoneFrame(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(360.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1A1E24))
            .border(2.dp, GarageColors.HairlineStrong, RoundedCornerShape(28.dp)),
    ) {
        // Status bar strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color(0xFF10141A)),
        )
        // Dynamic island
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .width(60.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0A0C0E)),
        )
        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Illustrations
// ---------------------------------------------------------------------------

@Composable
internal fun LocationPermissionIllustration() {
    PhoneFrame {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2B2F35))
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GarageColors.Accent),
            )
            Spacer(Modifier.height(8.dp))
            Text("Allow location?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "While using the app\nOnly this time\nDon't allow",
                color = GarageColors.TextDim, fontSize = 11.sp, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            // Precise location toggle row — highlighted
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GarageColors.AccentSoft)
                    .border(1.dp, GarageColors.AccentLine, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Precise location",
                    color = GarageColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GarageColors.Accent),
                )
            }
        }
    }
}

@Composable
internal fun BackgroundLocationIllustration() {
    PhoneFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                "Location permission",
                color = GarageColors.TextDim, fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val options = listOf(
                "Allow all the time" to true,
                "Allow while using" to false,
                "Ask every time" to false,
                "Don't allow" to false,
            )
            options.forEachIndexed { i, (label, highlighted) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (highlighted) GarageColors.AccentSoft else Color.Transparent)
                        .border(
                            if (highlighted) 1.dp else 0.dp,
                            GarageColors.AccentLine, RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (highlighted) GarageColors.Accent else GarageColors.Surface2)
                            .border(
                                1.dp,
                                if (highlighted) GarageColors.Accent else GarageColors.HairlineStrong,
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        color = if (highlighted) GarageColors.Accent else GarageColors.TextDim,
                        fontSize = 11.sp,
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (i < options.lastIndex) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
internal fun NotificationsIllustration() {
    PhoneFrame {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2B2F35))
                .padding(16.dp),
        ) {
            Text("Allow notifications?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Get alerted when your garage opens automatically.",
                color = GarageColors.TextDim, fontSize = 11.sp, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2E3338)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Don't allow", color = GarageColors.TextDim, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GarageColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Allow", color = GarageColors.AccentDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun BatteryIllustration() {
    // 0 = App Info screen, 1 = Battery options screen
    var scene by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            scene = 1
            delay(2500)
            scene = 0
        }
    }

    PhoneFrame {
        AnimatedContent(
            targetState = scene,
            transitionSpec = {
                if (targetState > initialState)
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                else
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
            },
            label = "battery_scene",
        ) { s ->
            when (s) {
                0 -> BatteryAppInfoScene()
                else -> BatteryOptionsScene()
            }
        }
    }
}

@Composable
private fun BatteryAppInfoScene() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            "App Info",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        val rows = listOf("Storage" to false, "Battery" to true, "Permissions" to false, "Notifications" to false)
        rows.forEach { (label, highlighted) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (highlighted) GarageColors.AccentSoft else Color(0xFF1E2228))
                    .border(if (highlighted) 1.dp else 0.dp, GarageColors.AccentLine, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    color = if (highlighted) GarageColors.Accent else GarageColors.TextDim,
                    fontSize = 11.sp,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "›",
                    color = if (highlighted) GarageColors.Accent else GarageColors.TextFaint,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun BatteryOptionsScene() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            "Battery",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val options = listOf(
            Triple("Unrestricted", "Allows background use.", true),
            Triple("Optimized", "", false),
            Triple("Restricted", "", false),
        )
        options.forEachIndexed { i, (label, sub, highlighted) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (highlighted) GarageColors.AccentSoft else Color(0xFF1E2228))
                    .border(if (highlighted) 1.dp else 0.dp, GarageColors.AccentLine, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (highlighted) GarageColors.Accent else Color(0xFF2E3338))
                            .border(1.dp, if (highlighted) GarageColors.Accent else GarageColors.HairlineStrong, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        color = if (highlighted) GarageColors.Accent else GarageColors.TextDim,
                        fontSize = 11.sp,
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (sub.isNotEmpty()) {
                    Text(sub, color = GarageColors.TextDim, fontSize = 9.sp, modifier = Modifier.padding(start = 20.dp, top = 2.dp))
                }
            }
            if (i < options.lastIndex) Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
internal fun UnusedAppIllustration() {
    PhoneFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                "Unused app settings",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Text(
                "Garage Opener",
                color = GarageColors.TextDim, fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            // Toggle row — highlighted, showing toggle OFF (desired state)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2228))
                    .border(1.dp, GarageColors.AccentLine, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Pause app activity\nif unused",
                    color = Color.White, fontSize = 10.sp, lineHeight = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                // Toggle OFF: grey pill, thumb on left
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3A3F45))
                        .border(1.dp, GarageColors.HairlineStrong, RoundedCornerShape(10.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(16.dp)
                            .align(Alignment.CenterStart)
                            .clip(CircleShape)
                            .background(GarageColors.TextDim),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "← Turn this OFF",
                color = GarageColors.Accent, fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

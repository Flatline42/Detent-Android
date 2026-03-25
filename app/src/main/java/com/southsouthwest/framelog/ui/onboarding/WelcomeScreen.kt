package com.southsouthwest.framelog.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Full-screen welcome composable for the first step of the DETENT onboarding flow.
 *
 * There is no TopAppBar on this screen — it fills the entire display surface.
 * Content is centered with weighted spacers so the version stamp sits at the bottom.
 *
 * Tapping "Skip setup" reveals a brief informational message before calling [onSkipSetup],
 * giving the user a moment to read that onboarding is re-runnable from Settings.
 *
 * @param onShowMeAround Called when the user taps "Show me around". The caller should advance
 *   the onboarding step and navigate to the first coached destination (GearLibrary).
 * @param onSkipSetup Called after a short delay when the user taps "Skip setup". The caller
 *   should mark onboarding complete and navigate to QuickScreen.
 */
@Composable
fun WelcomeScreen(
    onShowMeAround: () -> Unit,
    onSkipSetup: () -> Unit,
) {
    val context = LocalContext.current

    // Read versionName from PackageManager; fall back to "—" on any failure.
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        }.getOrDefault("—")
    }

    // Drives the AnimatedVisibility for the "you can re-run this" message and the
    // delayed call to onSkipSetup so the user can read it before navigating away.
    var showSkipMessage by remember { mutableStateOf(false) }

    // When showSkipMessage becomes true, wait 2 seconds then invoke onSkipSetup.
    LaunchedEffect(showSkipMessage) {
        if (showSkipMessage) {
            delay(2_000)
            onSkipSetup()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Large top spacer — pushes content down toward the visual center.
            Spacer(modifier = Modifier.weight(1.5f))

            // App icon stand-in (pen/create icon at large size).
            Icon(
                imageVector = Icons.Default.Create,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App name.
            Text(
                text = "DETENT",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Tagline.
            Text(
                text = "a film photography field journal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Body copy — centered, muted.
            Text(
                text = "Welcome to DETENT \u2014 a shot logger designed for speed and workflow. " +
                    "The goal is to log a frame in under 5 seconds from the Quick Screen, " +
                    "or under 3 seconds from the home screen widget.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Primary CTA.
            Button(
                onClick = onShowMeAround,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Show me around")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary / skip action.
            TextButton(
                onClick = { showSkipMessage = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !showSkipMessage,
            ) {
                Text("Skip setup")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Appears after "Skip setup" is tapped — reassures the user they can return.
            AnimatedVisibility(visible = showSkipMessage) {
                Text(
                    text = "No problem. You can run this introduction any time from " +
                        "Settings \u2192 Onboarding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Flexible bottom spacer — pushes version stamp to the bottom.
            Spacer(modifier = Modifier.weight(1f))

            // Version stamp.
            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

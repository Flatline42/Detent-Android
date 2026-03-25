package com.southsouthwest.framelog.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

// TODO: Replace with actual Ko-fi profile URL before release
private const val TIP_JAR_URL = "https://ko-fi.com/"

/**
 * Widget setup instructions screen.
 *
 * Reachable from two entry points:
 *   1. **Settings → Widget setup instructions** — [fromOnboarding] = false. Back arrow navigates
 *      to the Settings screen via [navController.popBackStack].
 *   2. **Onboarding step 11** — [fromOnboarding] = true. Back arrow is hidden. The "Done"
 *      button calls [onboardingViewModel.complete], and the NavGraph reacts to the COMPLETE
 *      state by navigating to QuickScreen.
 *
 * When [fromOnboarding] is true, the screen also shows:
 *   - An explanatory footer about the widget's value proposition.
 *   - A Ko-fi tip jar card.
 *
 * @param navController Used for back navigation when [fromOnboarding] = false.
 * @param fromOnboarding True when navigated to from the onboarding flow.
 * @param onboardingViewModel Used to call [OnboardingViewModel.complete] when the user
 *   taps "Done" at the end of the onboarding flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSetupScreen(
    navController: NavHostController,
    fromOnboarding: Boolean,
    onboardingViewModel: OnboardingViewModel,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget Setup") },
                navigationIcon = {
                    // Back arrow is only shown when not in the onboarding flow.
                    if (!fromOnboarding) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── How to add the widget ──────────────────────────────────────
            item {
                Text(
                    text = "HOW TO ADD THE WIDGET",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InstructionStep(
                        number = 1,
                        text = "Long-press on your home screen until the edit mode appears.",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InstructionStep(
                        number = 2,
                        text = "Tap 'Widgets' (or the + icon, depending on your launcher).",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InstructionStep(
                        number = 3,
                        text = "Find DETENT in the widget list.",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InstructionStep(
                        number = 4,
                        text = "Long-press the 4\u00d72 DETENT widget and drag it to your home screen.",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InstructionStep(
                        number = 5,
                        text = "Tap the widget to confirm it's connected to your active roll.",
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item { HorizontalDivider() }

            // ── GPS note ──────────────────────────────────────────────────
            item {
                Text(
                    text = "Note: The widget cannot capture GPS locations due to Android limitations. " +
                        "Log GPS coordinates from the Quick Screen instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            item { HorizontalDivider() }

            // ── Onboarding-only footer ─────────────────────────────────────
            if (fromOnboarding) {
                item { Spacer(modifier = Modifier.height(16.dp)) }

                item {
                    Text(
                        text = "One more thing",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    Text(
                        text = "The 2\u00d74 widget gives you aperture, shutter speed, and one-tap frame " +
                            "logging without unlocking your phone \u2014 under 3 seconds per frame. " +
                            "You can revisit this introduction any time from Settings \u2192 Onboarding.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                item { HorizontalDivider() }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // Ko-fi tip jar card.
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        border = BorderStroke(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "If DETENT is useful to you",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "consider buying me a roll of film \u2615",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(TIP_JAR_URL)),
                                    )
                                },
                            ) {
                                Text("Tip jar  \u203a")
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // ── Done button ────────────────────────────────────────────────
            item {
                Button(
                    onClick = {
                        if (fromOnboarding) {
                            // Calling complete() writes onboardingComplete = true to
                            // SharedPreferences and emits COMPLETE to the step StateFlow.
                            // NavGraph's LaunchedEffect(onboardingStep) reacts and navigates
                            // to QuickScreen, clearing the back stack.
                            onboardingViewModel.complete()
                        } else {
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Instruction step row
// ---------------------------------------------------------------------------

/**
 * A single numbered instruction step rendered as inline text.
 * Example: "1. Long-press on your home screen…"
 */
@Composable
private fun InstructionStep(
    number: Int,
    text: String,
) {
    Text(
        text = "$number. $text",
        style = MaterialTheme.typography.bodyMedium,
    )
}

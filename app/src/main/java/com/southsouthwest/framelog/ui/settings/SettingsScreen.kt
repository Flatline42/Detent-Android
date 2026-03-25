package com.southsouthwest.framelog.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import com.southsouthwest.framelog.data.AppTheme
import com.southsouthwest.framelog.data.ExportFormat
import com.southsouthwest.framelog.ui.navigation.Welcome
import com.southsouthwest.framelog.ui.navigation.WidgetSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// TODO: Replace with actual Ko-fi profile URL before release
private const val TIP_JAR_URL = "https://ko-fi.com/"


// ---------------------------------------------------------------------------
// Display labels for preference enums (private to this file)
// ---------------------------------------------------------------------------

private val ExportFormat.label: String
    get() = when (this) {
        ExportFormat.CSV -> "CSV"
        ExportFormat.JSON -> "JSON"
        ExportFormat.PLAIN_TEXT -> "Plain text"
    }

private val AppTheme.label: String
    get() = when (this) {
        AppTheme.LIGHT -> "Light"
        AppTheme.DARK -> "Dark"
        AppTheme.SYSTEM -> "System"
    }

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        }.getOrDefault("—")
    }

    // ── Dialog visibility state ────────────────────────────────────────────
    var showExtraFramesDialog by remember { mutableStateOf(false) }
    var draftExtraFrames by remember { mutableIntStateOf(state.extraFramesPerRoll) }

    var showExportFormatDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    var showResetStage1Dialog by remember { mutableStateOf(false) }
    var showResetStage2Dialog by remember { mutableStateOf(false) }
    var showResetRestartDialog by remember { mutableStateOf(false) }

    var showFontLicensesDialog by remember { mutableStateOf(false) }
    val fontCreditsText = remember {
        runCatching {
            context.assets.open("licenses/FONT_CREDITS.txt").bufferedReader().readText()
        }.getOrDefault("Font credits unavailable.")
    }

    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    val privacyPolicyText = remember {
        runCatching {
            context.assets.open("privacy_policy.txt").bufferedReader().readText()
        }.getOrDefault("Privacy policy unavailable.")
    }

    // ── File picker for backup restore ────────────────────────────────────
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    // ── Event handling ────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShareBackupFile -> {
                    val file = File(event.filePath)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "DETENT backup")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share backup file"))
                }

                is SettingsEvent.RestoreCompleteRestartRequired -> {
                    showRestartDialog = true
                }

                is SettingsEvent.ResetCompleteRestartRequired -> {
                    showResetRestartDialog = true
                }

                is SettingsEvent.OpenTipJar -> {
                    openUrl(context, TIP_JAR_URL)
                }

                is SettingsEvent.OpenOssLicenses -> {
                    context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                }

                is SettingsEvent.NavigateToOnboarding -> {
                    navController.navigate(Welcome)
                }

                is SettingsEvent.NavigateToWidgetSetup -> {
                    navController.navigate(WidgetSetup(fromOnboarding = false))
                }

                is SettingsEvent.ShowErrorMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }

                is SettingsEvent.ShowInfoMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── Shooting Defaults ──────────────────────────────────────────
            item { SettingsSectionHeader("SHOOTING DEFAULTS") }

            item {
                SettingsRow(
                    label = "Extra frames per roll",
                    value = "${state.extraFramesPerRoll}",
                    onClick = {
                        draftExtraFrames = state.extraFramesPerRoll
                        showExtraFramesDialog = true
                    },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }
            item {
                SettingsRow(
                    label = "GPS capture",
                    trailing = {
                        Switch(
                            checked = state.gpsCaptureEnabled,
                            onCheckedChange = viewModel::onGpsCaptureEnabledChanged,
                        )
                    },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }
            item {
                SettingsRow(
                    label = "Default export format",
                    value = state.defaultExportFormat.label,
                    onClick = { showExportFormatDialog = true },
                )
            }

            item { HorizontalDivider() }

            // ── Appearance ─────────────────────────────────────────────────
            item { SettingsSectionHeader("APPEARANCE") }

            item {
                SettingsRow(
                    label = "App theme",
                    value = state.appTheme.label,
                    onClick = { showThemeDialog = true },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }
            item {
                SettingsRow(
                    label = "Accessible color mode",
                    sublabel = "coming soon",
                    trailing = {
                        Switch(
                            checked = state.accessibleColorMode,
                            onCheckedChange = viewModel::onAccessibleColorModeChanged,
                            enabled = false,
                        )
                    },
                )
            }

            item { HorizontalDivider() }

            // ── Data & Backup ──────────────────────────────────────────────
            item { SettingsSectionHeader("DATA & BACKUP") }

            item {
                SettingsRow(
                    label = "Export backup",
                    onClick = viewModel::onExportBackupTapped,
                    trailing = if (state.isExportingBackup) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else null,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }
            item {
                SettingsRow(
                    label = "Restore from backup",
                    onClick = { filePickerLauncher.launch("*/*") },
                    trailing = if (state.isRestoringBackup) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else null,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }
            item {
                DangerSettingsRow(
                    label = "Reset database",
                    onClick = { showResetStage1Dialog = true },
                )
            }

            item { HorizontalDivider() }

            // ── Widget ─────────────────────────────────────────────────────
            item { SettingsSectionHeader("WIDGET") }

            item {
                SettingsRow(
                    label = "Widget setup instructions",
                    onClick = viewModel::onWidgetSetupTapped,
                )
            }

            item { HorizontalDivider() }

            // ── Onboarding ─────────────────────────────────────────────────
            item { SettingsSectionHeader("ONBOARDING") }

            item {
                SettingsRow(
                    label = "Re-run introduction",
                    onClick = viewModel::onRerunOnboardingTapped,
                )
            }

            item { HorizontalDivider() }

            // ── Support ────────────────────────────────────────────────────
            item { SettingsSectionHeader("SUPPORT") }

            item { SupportRow(onTipJarTapped = viewModel::onTipJarTapped) }

            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }

            // Debug: reset tip nag flag so the prompt can fire again during testing
            item {
                SettingsRow(
                    label = "Reset tip nag flag (debug)",
                    onClick = viewModel::onResetTipNagFlag,
                )
            }

            item { HorizontalDivider() }

            // ── About ──────────────────────────────────────────────────────
            item { SettingsSectionHeader("ABOUT") }

            item {
                // Version — read-only, no chevron
                SettingsRow(
                    label = "Version",
                    value = versionName,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }
            item {
                SettingsRow(
                    label = "Privacy policy",
                    onClick = { showPrivacyPolicyDialog = true },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }
            item {
                SettingsRow(
                    label = "Font licenses",
                    onClick = { showFontLicensesDialog = true },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(start = 16.dp)) }
            item {
                SettingsRow(
                    label = "Open source licenses",
                    onClick = viewModel::onOssLicensesTapped,
                )
            }

            // ── Footer ─────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "DETENT",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "made by a film photographer, for film photographers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        }
    }

    // ── Extra frames dialog ────────────────────────────────────────────────
    if (showExtraFramesDialog) {
        AlertDialog(
            onDismissRequest = { showExtraFramesDialog = false },
            title = { Text("Extra frames per roll") },
            text = {
                Column {
                    Text(
                        text = "Added to each roll\u2019s frame count to account for the film leader and slight over-capacity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { if (draftExtraFrames > 0) draftExtraFrames-- },
                            enabled = draftExtraFrames > 0,
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("\u2212")
                        }
                        Spacer(Modifier.width(20.dp))
                        Text(
                            text = "$draftExtraFrames",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.width(20.dp))
                        OutlinedButton(
                            onClick = { if (draftExtraFrames < 10) draftExtraFrames++ },
                            enabled = draftExtraFrames < 10,
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("+")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onExtraFramesChanged(draftExtraFrames)
                        showExtraFramesDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showExtraFramesDialog = false }) { Text("Cancel") }
            },
        )
    }

    // ── Default export format dialog ───────────────────────────────────────
    if (showExportFormatDialog) {
        SingleChoiceDialog(
            title = "Default export format",
            options = listOf(
                ExportFormat.CSV to "CSV",
                ExportFormat.JSON to "JSON",
                ExportFormat.PLAIN_TEXT to "Plain text",
            ),
            current = state.defaultExportFormat,
            onDismiss = { showExportFormatDialog = false },
            onSelected = {
                viewModel.onDefaultExportFormatChanged(it)
                showExportFormatDialog = false
            },
        )
    }

    // ── App theme dialog ───────────────────────────────────────────────────
    if (showThemeDialog) {
        SingleChoiceDialog(
            title = "App theme",
            options = listOf(
                AppTheme.SYSTEM to "System default",
                AppTheme.LIGHT to "Light",
                AppTheme.DARK to "Dark",
            ),
            current = state.appTheme,
            onDismiss = { showThemeDialog = false },
            onSelected = {
                viewModel.onAppThemeChanged(it)
                showThemeDialog = false
            },
        )
    }

    // ── Restore confirmation dialog ────────────────────────────────────────
    if (showRestoreConfirmDialog) {
        val uriToRestore = pendingRestoreUri
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
            title = { Text("Restore from backup?") },
            text = {
                Text("This will permanently overwrite ALL current data with the contents of the backup file. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        if (uriToRestore != null) {
                            pendingRestoreUri = null
                            scope.launch {
                                val filePath = copyUriToCache(context, uriToRestore)
                                if (filePath != null) {
                                    viewModel.onRestoreBackupConfirmed(filePath)
                                } else {
                                    snackbarHostState.showSnackbar("Could not read the selected backup file")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                    },
                ) { Text("Cancel") }
            },
        )
    }

    // ── Restart required dialog (restore) ─────────────────────────────────
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restore complete") },
            text = { Text("Restart DETENT to use the restored data.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartDialog = false
                        restartApp(context)
                    },
                ) { Text("Restart now") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) { Text("Later") }
            },
        )
    }

    // ── Reset database — stage 1 ───────────────────────────────────────────
    if (showResetStage1Dialog) {
        AlertDialog(
            onDismissRequest = { showResetStage1Dialog = false },
            title = { Text("Reset database?") },
            text = {
                Text("This will permanently delete all rolls, frames, and gear. This cannot be undone unless you have a backup.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetStage1Dialog = false
                        showResetStage2Dialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showResetStage1Dialog = false }) { Text("Cancel") }
            },
        )
    }

    // ── Reset database — stage 2 (final confirmation) ─────────────────────
    if (showResetStage2Dialog) {
        AlertDialog(
            onDismissRequest = { showResetStage2Dialog = false },
            title = { Text("This cannot be undone.") },
            text = {
                Text("You are about to wipe the entire database. Every roll, frame, lens, camera body, filter, film stock, and kit will be deleted. Settings are preserved. There is no recovery without a backup.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetStage2Dialog = false
                        viewModel.onResetDatabaseConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Reset now") }
            },
            dismissButton = {
                TextButton(onClick = { showResetStage2Dialog = false }) { Text("Cancel") }
            },
        )
    }

    // ── Restart required dialog (reset) ───────────────────────────────────
    if (showResetRestartDialog) {
        AlertDialog(
            onDismissRequest = { showResetRestartDialog = false },
            title = { Text("Database reset") },
            text = { Text("The database has been wiped. Restart DETENT to begin fresh.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetRestartDialog = false
                        restartApp(context)
                    },
                ) { Text("Restart now") }
            },
            dismissButton = {
                TextButton(onClick = { showResetRestartDialog = false }) { Text("Later") }
            },
        )
    }

    // ── Privacy policy dialog ──────────────────────────────────────────────
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text("Privacy Policy") },
            text = { Text(privacyPolicyText) },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicyDialog = false }) { Text("Close") }
            },
        )
    }

    // ── Font licenses dialog ───────────────────────────────────────────────
    if (showFontLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showFontLicensesDialog = false },
            title = { Text("Font licenses") },
            text = { Text(fontCreditsText) },
            confirmButton = {
                TextButton(onClick = { showFontLicensesDialog = false }) { Text("Close") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Reusable row composables
// ---------------------------------------------------------------------------

/**
 * Standard settings row. Behaviour:
 *  - [onClick] + [value]: shows "value  ›" on the right
 *  - [onClick] only: shows bare "›" on the right
 *  - [value] only (no onClick): shows value with no chevron (read-only)
 *  - [trailing] overrides the right side entirely (e.g. Switch, progress indicator)
 */
@Composable
private fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    onClick: (() -> Unit)? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            trailing != null -> trailing()
            value != null && onClick != null -> Text(
                text = "$value  \u203a",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            value != null -> Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onClick != null -> Text(
                text = "\u203a",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A settings row styled as a danger action — label in error color, no chevron.
 * Used for irreversible operations like "Reset database".
 */
@Composable
private fun DangerSettingsRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** The "Buy me a coffee" support row with descriptive text and an outlined tip jar button. */
@Composable
private fun SupportRow(onTipJarTapped: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Buy me a coffee",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "If DETENT is useful to you, a tip is always appreciated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onTipJarTapped) {
            Text("tip jar  \u203a")
        }
    }
}

// ---------------------------------------------------------------------------
// Generic single-choice dialog (GPS precision, export format, app theme)
// ---------------------------------------------------------------------------

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (option, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = { onSelected(option) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun restartApp(context: Context) {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) }
    if (launchIntent != null) context.startActivity(launchIntent)
    (context as? Activity)?.finishAffinity()
}

/**
 * Copies the content at [uri] (e.g. a .framelog file from the system file picker) to the
 * app's cache directory so the ViewModel can restore from a plain file path.
 * Returns the absolute path of the temporary file, or null on failure.
 */
private suspend fun copyUriToCache(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext null
            val tempFile = File(context.cacheDir, "restore_temp.detent")
            inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile.absolutePath
        }.getOrNull()
    }

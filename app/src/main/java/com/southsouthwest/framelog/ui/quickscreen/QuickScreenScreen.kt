package com.southsouthwest.framelog.ui.quickscreen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.android.gms.location.FusedLocationProviderClient
import com.southsouthwest.framelog.ui.onboarding.OnboardingStep
import com.southsouthwest.framelog.ui.onboarding.OnboardingViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.relation.RollWithDetails
import com.southsouthwest.framelog.ui.navigation.RollJournal
import com.southsouthwest.framelog.ui.navigation.RollSetup
import com.southsouthwest.framelog.ui.util.ExposureValues
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Screen entry point
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickScreenScreen(
    navController: NavHostController,
    onboardingViewModel: OnboardingViewModel? = null,
) {
    val viewModel: QuickScreenViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onboardingStep by (onboardingViewModel?.step?.collectAsState()
        ?: remember { mutableStateOf(OnboardingStep.COMPLETE) })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // GPS — FusedLocationProviderClient for frame log capture
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var pendingLogAfterPermission by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (pendingLogAfterPermission) {
            pendingLogAfterPermission = false
            if (granted) {
                scope.launch { getLocationAndLog(fusedLocationClient, viewModel) }
            } else {
                // Permission denied — log without GPS rather than blocking the user
                viewModel.onLogFrameTapped(null, null)
            }
        }
    }

    // Speech — dictate the note field
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        matches?.firstOrNull()?.let { viewModel.onNoteChanged(it) }
    }

    // Dialog / sheet state
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var overwriteFrameNumber by remember { mutableIntStateOf(0) }
    var showRollCompleteDialog by remember { mutableStateOf(false) }
    var showFilterPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickScreenEvent.FrameLogged -> {
                    vibrateConfirm(context)
                }
                is QuickScreenEvent.ConfirmOverwrite -> {
                    overwriteFrameNumber = event.frameNumber
                    showOverwriteDialog = true
                }
                is QuickScreenEvent.RollComplete -> showRollCompleteDialog = true
                is QuickScreenEvent.ShowErrorMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    // Initiates Log Frame, requesting GPS location first if the roll requires it.
    fun triggerLogFrame() {
        val gpsEnabled = state.activeRoll?.roll?.gpsEnabled == true
        if (!gpsEnabled) {
            viewModel.onLogFrameTapped(null, null)
            return
        }
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            scope.launch { getLocationAndLog(fusedLocationClient, viewModel) }
        } else {
            pendingLogAfterPermission = true
            locationPermissionLauncher.launch(permission)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.activeRoll == null -> {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onCreateRoll = { navController.navigate(RollSetup) },
                )
            }

            else -> {
                // Safe: we just confirmed activeRoll is non-null above
                val activeRoll = state.activeRoll!!
                val rollLenses = activeRoll.lenses
                val rollFilters = activeRoll.filters.map { it.filter }

                // Compute visible filter chips: MRU-ordered, up to 4, then pad with remaining
                val mruSet = state.mruFilterIds.toSet()
                val displayedFilters = buildList {
                    state.mruFilterIds.take(4)
                        .mapNotNull { id -> rollFilters.firstOrNull { it.id == id } }
                        .also { addAll(it) }
                    if (size < 4) {
                        rollFilters.filter { it.id !in mruSet }.take(4 - size).also { addAll(it) }
                    }
                }
                val hasMoreFilters = rollFilters.size > 4

                // Stepper index computations — captured once to avoid smart-cast issues
                val apertures = state.availableApertures
                val apertureIndex = apertures.indexOf(state.aperture).takeIf { it >= 0 } ?: 0

                val shutterSpeeds = state.availableShutterSpeeds
                val shutterIndex = shutterSpeeds.indexOf(state.shutterSpeed).takeIf { it >= 0 } ?: 0

                val ecVals = ExposureValues.exposureCompensationValues
                val ecZeroIdx = ecVals.indexOfFirst { abs(it) < 0.001f }
                val ec = state.exposureCompensation
                val ecIdx = ec
                    ?.let { v -> ecVals.indexOfFirst { abs(it - v) < 0.001f } }
                    ?.takeIf { it >= 0 }
                    ?: ecZeroIdx

                // EV totals for header status line
                val activeFilterObjs = rollFilters.filter { it.id in state.activeFilterIds }
                val filterEvSum = activeFilterObjs.fold(0f) { acc, f -> acc + (f.evReduction ?: 0f) }
                val filterHasNullEv = activeFilterObjs.any { it.evReduction == null }
                val totalEv = (ec ?: 0f) + filterEvSum
                val totalEvText: String? = if (ec != null || activeFilterObjs.isNotEmpty()) {
                    val tilde = if (filterHasNullEv) "~" else ""
                    val sign = if (totalEv > 0f) "+" else ""
                    "${tilde}${sign}${"%.1f".format(totalEv)} EV"
                } else null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // ── Header ──────────────────────────────────────────────
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            if (onboardingStep == OnboardingStep.QS_HEADER) {
                                onboardingViewModel?.updateSpotlightBounds(coords.boundsInWindow())
                            }
                        },
                    ) {
                        QuickScreenHeader(
                            rollName = activeRoll.roll.name,
                            hasMultipleRolls = state.loadedRolls.size > 1,
                            activeFilterCount = activeFilterObjs.size,
                            currentFrameNumber = state.currentFrameNumber,
                            totalFrames = activeRoll.frames.size,
                            totalEvText = totalEvText,
                            onHeaderTapped = viewModel::onHeaderTapped,
                        )
                    }
                    HorizontalDivider()

                    // ── Scrollable interactive controls ──────────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        // Frame pointer stepper
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                if (onboardingStep == OnboardingStep.QS_FRAME) {
                                    onboardingViewModel?.updateSpotlightBounds(coords.boundsInWindow())
                                }
                            },
                        ) {
                        SmallStepper(
                            label = "frame pointer",
                            displayText = "${state.currentFrameNumber} / ${activeRoll.frames.size}",
                            canDecrement = state.currentFrameNumber > 1,
                            canIncrement = true, // advancing past last emits RollComplete
                            onDecrement = {
                                viewModel.onFramePointerChanged(state.currentFrameNumber - 1)
                            },
                            onIncrement = {
                                viewModel.onFramePointerChanged(state.currentFrameNumber + 1)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        }

                        // Off-frontier indicator — shown when the user has manually navigated
                        // away from the next expected unlogged frame.
                        val frontier = state.frontierFrameNumber
                        // isAtFrontier is true when roll is complete (frontier == null) or pointer
                        // is already on the next expected unlogged frame.
                        val isAtFrontier = frontier == null || state.currentFrameNumber == frontier
                        if (!isAtFrontier) {
                            // Smart-cast: !isAtFrontier implies frontier != null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "not at current frame",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { viewModel.onFramePointerChanged(frontier) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = "← return to $frontier",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Lens selector
                        val selectedLens = rollLenses
                            .firstOrNull { it.lens.id == state.selectedLensId }?.lens
                            ?: rollLenses.firstOrNull()?.lens
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                if (onboardingStep == OnboardingStep.QS_LENS) {
                                    onboardingViewModel?.updateSpotlightBounds(coords.boundsInWindow())
                                }
                            },
                        ) {
                            LensRow(
                                lensName = selectedLens?.name,
                                hasMultipleLenses = rollLenses.size > 1,
                                onCycleLens = viewModel::onLensCycleTapped,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Filter chips + EV sum
                        Box(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                if (onboardingStep == OnboardingStep.QS_FILTERS) {
                                    onboardingViewModel?.updateSpotlightBounds(coords.boundsInWindow())
                                }
                            },
                        ) {
                            FilterChipsRow(
                                displayedFilters = displayedFilters,
                                activeFilterIds = state.activeFilterIds,
                                hasMoreFilters = hasMoreFilters || rollFilters.isNotEmpty(),
                                filterEvSum = filterEvSum,
                                filterHasNullEv = filterHasNullEv,
                                activeCount = activeFilterObjs.size,
                                onFilterToggled = viewModel::onFilterToggled,
                                onOpenPicker = { showFilterPicker = true },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Exposure compensation — tapping the value clears it (sets to null)
                        SmallStepper(
                            label = "exposure compensation",
                            displayText = ec?.let {
                                ExposureValues.formatExposureCompensation(it) + " EV"
                            } ?: "0 EV",
                            displayColor = if (ec != null) MaterialTheme.colorScheme.onSurface
                                          else MaterialTheme.colorScheme.onSurfaceVariant,
                            canDecrement = ecIdx > 0,
                            canIncrement = ecIdx < ecVals.size - 1,
                            onDecrement = {
                                if (ecIdx > 0) viewModel.onExposureCompensationChanged(ecVals[ecIdx - 1])
                            },
                            onIncrement = {
                                if (ecIdx < ecVals.size - 1) {
                                    viewModel.onExposureCompensationChanged(ecVals[ecIdx + 1])
                                }
                            },
                            onValueTapped = if (ec != null) {
                                { viewModel.onExposureCompensationChanged(null) }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Aperture + shutter steppers — wrapped together for QS_STEPPERS spotlight
                        Column(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                if (onboardingStep == OnboardingStep.QS_STEPPERS) {
                                    onboardingViewModel?.updateSpotlightBounds(coords.boundsInWindow())
                                }
                            },
                        ) {
                        // Aperture — + = wider (lower index), − = narrower (higher index)
                        LargeStepper(
                            label = "aperture",
                            displayText = state.aperture ?: "—",
                            canDecrement = apertureIndex < apertures.size - 1,
                            canIncrement = apertureIndex > 0,
                            onDecrement = {
                                if (apertureIndex < apertures.size - 1) {
                                    viewModel.onApertureChanged(apertures[apertureIndex + 1])
                                }
                            },
                            onIncrement = {
                                if (apertureIndex > 0) {
                                    viewModel.onApertureChanged(apertures[apertureIndex - 1])
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(8.dp))

                        // Shutter speed — + = slower (lower index), − = faster (higher index)
                        val shutterDisplay = state.shutterSpeed
                            ?.let { ExposureValues.shutterDisplayValue(it) } ?: "—"
                        val isLongExposure = state.shutterSpeed
                            ?.let { ExposureValues.isLongExposure(it) } ?: false
                        LargeStepper(
                            label = "shutter speed",
                            displayText = shutterDisplay,
                            displayColor = if (isLongExposure) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onSurface,
                            canDecrement = shutterIndex < shutterSpeeds.size - 1,
                            canIncrement = shutterIndex > 0,
                            onDecrement = {
                                if (shutterIndex < shutterSpeeds.size - 1) {
                                    viewModel.onShutterSpeedChanged(shutterSpeeds[shutterIndex + 1])
                                }
                            },
                            onIncrement = {
                                if (shutterIndex > 0) {
                                    viewModel.onShutterSpeedChanged(shutterSpeeds[shutterIndex - 1])
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        } // end Column wrapper for QS_STEPPERS

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Note + Mic
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            OutlinedTextField(
                                value = state.note,
                                onValueChange = viewModel::onNoteChanged,
                                modifier = Modifier.weight(1f),
                                label = { Text("note") },
                                placeholder = { Text("tap to type\u2026") },
                                minLines = 1,
                                maxLines = 3,
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                        )
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate frame note")
                                    }
                                    speechLauncher.launch(intent)
                                },
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(56.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("Mic", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    HorizontalDivider()

                    // ── Log Frame button — anchored to bottom ──────────────
                    Button(
                        onClick = { triggerLogFrame() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(64.dp)
                            .onGloballyPositioned { coords ->
                                if (onboardingStep == OnboardingStep.QS_LOG_FRAME) {
                                    onboardingViewModel?.updateSpotlightBounds(coords.boundsInWindow())
                                }
                            },
                        enabled = !state.isLoggingFrame,
                    ) {
                        if (state.isLoggingFrame) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = "log frame",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Overwrite confirmation dialog ──────────────────────────────────────
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("Overwrite Frame $overwriteFrameNumber?") },
            text = { Text("Frame $overwriteFrameNumber has already been logged. Overwrite it with the current data?") },
            confirmButton = {
                Button(
                    onClick = {
                        showOverwriteDialog = false
                        viewModel.onOverwriteConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteDialog = false }) { Text("Cancel") }
            },
        )
    }

    // ── Roll complete dialog ───────────────────────────────────────────────
    if (showRollCompleteDialog) {
        val rollId = state.activeRoll?.roll?.id
        AlertDialog(
            onDismissRequest = { showRollCompleteDialog = false },
            title = { Text("Roll is Full") },
            text = { Text("You\u2019ve reached the end of this roll. Open the Roll Journal to finish the roll.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRollCompleteDialog = false
                        if (rollId != null) navController.navigate(RollJournal(rollId))
                    },
                ) { Text("Open Journal") }
            },
            dismissButton = {
                TextButton(onClick = { showRollCompleteDialog = false }) { Text("Dismiss") }
            },
        )
    }

    // ── Switch Roll bottom sheet ───────────────────────────────────────────
    if (state.showSwitchRollSheet) {
        SwitchRollBottomSheet(
            loadedRolls = state.loadedRolls,
            activeRollId = state.activeRoll?.roll?.id,
            onDismiss = viewModel::onSwitchRollSheetDismissed,
            onRollSelected = viewModel::onRollSwitched,
        )
    }

    // ── Filter picker bottom sheet ─────────────────────────────────────────
    if (showFilterPicker) {
        val rollFilters = state.activeRoll?.filters?.map { it.filter } ?: emptyList()
        FilterPickerBottomSheet(
            rollFilters = rollFilters,
            activeFilterIds = state.activeFilterIds,
            onFilterToggled = viewModel::onFilterToggled,
            onDismiss = { showFilterPicker = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun QuickScreenHeader(
    rollName: String,
    hasMultipleRolls: Boolean,
    activeFilterCount: Int,
    currentFrameNumber: Int,
    totalFrames: Int,
    totalEvText: String?,
    onHeaderTapped: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (hasMultipleRolls) {
                    Text(
                        text = "active roll  \u00b7  tap to switch",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (hasMultipleRolls) "$rollName  \u203a" else rollName,
                    style = MaterialTheme.typography.displayMedium,
                    textDecoration = if (hasMultipleRolls) TextDecoration.Underline else TextDecoration.None,
                    modifier = if (hasMultipleRolls) Modifier.clickable(onClick = onHeaderTapped) else Modifier,
                )
            }
            if (activeFilterCount > 0) {
                Text(
                    text = "$activeFilterCount filter${if (activeFilterCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Frame $currentFrameNumber / $totalFrames",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totalEvText != null) {
                Text(
                    text = totalEvText,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lens row — tap to cycle through the roll's lenses
// ---------------------------------------------------------------------------

@Composable
private fun LensRow(
    lensName: String?,
    hasMultipleLenses: Boolean,
    onCycleLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "lens",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedCard(
            onClick = onCycleLens,
            enabled = hasMultipleLenses,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = buildString {
                    append(lensName ?: "\u2014 no lens \u2014")
                    if (hasMultipleLenses) append("  \u203a")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Filter chips row — up to 4 MRU chips + EV sum + optional "+" picker button
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipsRow(
    displayedFilters: List<Filter>,
    activeFilterIds: Set<Int>,
    hasMoreFilters: Boolean,
    filterEvSum: Float,
    filterHasNullEv: Boolean,
    activeCount: Int,
    onFilterToggled: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "filters",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (activeCount > 0) {
                val tilde = if (filterHasNullEv) "~" else ""
                val sign = if (filterEvSum > 0f) "+" else ""
                Text(
                    text = "${tilde}${sign}${"%.1f".format(filterEvSum)} EV",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            displayedFilters.forEach { filter ->
                FilterChip(
                    selected = filter.id in activeFilterIds,
                    onClick = { onFilterToggled(filter.id) },
                    label = { Text(filter.name) },
                )
            }
            // "+" chip opens the full filter picker
            if (hasMoreFilters) {
                FilterChip(
                    selected = false,
                    onClick = onOpenPicker,
                    label = { Text("+") },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Switch Roll bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchRollBottomSheet(
    loadedRolls: List<RollWithDetails>,
    activeRollId: Int?,
    onDismiss: () -> Unit,
    onRollSelected: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "switch active roll",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
        )
        HorizontalDivider()

        loadedRolls.forEach { roll ->
            val isActive = roll.roll.id == activeRollId
            val loggedCount = roll.frames.count { it.isLogged }
            val totalCount = roll.frames.size
            val lastLoggedAt = roll.frames
                .filter { it.isLogged }
                .maxByOrNull { it.loggedAt ?: 0L }
                ?.loggedAt

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRollSelected(roll.roll.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isActive,
                    onClick = { onRollSelected(roll.roll.id) },
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = roll.roll.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "$loggedCount / $totalCount  \u00b7  last shot ${formatLastShot(lastLoggedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (totalCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { loggedCount / totalCount.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Filter picker bottom sheet — shows all available roll filters for toggling
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterPickerBottomSheet(
    rollFilters: List<Filter>,
    activeFilterIds: Set<Int>,
    onFilterToggled: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "select filters",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
        )
        HorizontalDivider()

        if (rollFilters.isEmpty()) {
            Text(
                text = "No filters on this roll",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rollFilters.forEach { filter ->
                    FilterChip(
                        selected = filter.id in activeFilterIds,
                        onClick = { onFilterToggled(filter.id) },
                        label = { Text(filter.name) },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Small stepper — used for Exposure Compensation and Frame Pointer
// ---------------------------------------------------------------------------

@Composable
private fun SmallStepper(
    label: String,
    displayText: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    displayColor: Color = Color.Unspecified,
    /** Optional: tapping the value display triggers this callback (e.g. clear EC). */
    onValueTapped: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(
                onClick = { vibrateDecrement(context); onDecrement() },
                enabled = canDecrement,
                modifier = Modifier.size(width = 42.dp, height = 36.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text("\u2212", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(36.dp)
                    .then(
                        if (onValueTapped != null) Modifier.clickable(onClick = onValueTapped)
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleSmall,
                    color = displayColor,
                    textDecoration = if (onValueTapped != null) TextDecoration.Underline
                                     else TextDecoration.None,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { vibrateIncrement(context); onIncrement() },
                enabled = canIncrement,
                modifier = Modifier.size(width = 42.dp, height = 36.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Large stepper — used for Aperture and Shutter Speed
// ---------------------------------------------------------------------------

@Composable
private fun LargeStepper(
    label: String,
    displayText: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    displayColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(
                onClick = { vibrateDecrement(context); onDecrement() },
                enabled = canDecrement,
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("\u2212", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = displayColor,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { vibrateIncrement(context); onIncrement() },
                enabled = canIncrement,
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state — shown when no rolls are loaded in any camera
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(
    onCreateRoll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "DETENT",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "no roll loaded — tap to create",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCreateRoll) {
                Text("New Roll")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// GPS location helper — called only after permission is confirmed
// ---------------------------------------------------------------------------

@SuppressLint("MissingPermission")
private suspend fun getLocationAndLog(
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: QuickScreenViewModel,
) {
    try {
        val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
        viewModel.onLogFrameTapped(location?.latitude, location?.longitude)
    } catch (_: Exception) {
        // Fallback: log without coordinates rather than blocking the user
        viewModel.onLogFrameTapped(null, null)
    }
}

// ---------------------------------------------------------------------------
// Last-shot timestamp formatter for the Switch Roll sheet
// ---------------------------------------------------------------------------

private fun formatLastShot(loggedAt: Long?): String {
    if (loggedAt == null) return "never"
    val now = System.currentTimeMillis()
    val diffMs = now - loggedAt
    val diffMinutes = diffMs / (60 * 1000L)
    val diffHours = diffMs / (60 * 60 * 1000L)
    val diffDays = diffMs / (24 * 60 * 60 * 1000L)
    return when {
        diffMinutes < 1 -> "just now"
        diffHours < 1 -> "$diffMinutes min ago"
        diffDays < 1 -> {
            // Same day — show clock time
            val cal = Calendar.getInstance().apply { timeInMillis = loggedAt }
            String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
        diffDays == 1L -> "yesterday"
        diffDays < 7 -> "$diffDays days ago"
        else -> "${diffDays / 7} week${if (diffDays / 7 > 1) "s" else ""} ago"
    }
}

// ---------------------------------------------------------------------------
// Haptic helpers
// ---------------------------------------------------------------------------

// All three use the identical waveform confirmed to fire on Pixel 7 (the double-pulse
// pattern that vibrateDecrement originally used). Using the same parameters for
// vibrateIncrement and vibrateConfirm isolates whether the issue is the waveform
// or the code path — if increment still doesn't fire with an identical waveform to
// decrement, the problem is elsewhere (e.g. Vibrator rate-limiting consecutive calls).
private fun vibrateDecrement(context: Context) {
    context.getSystemService(Vibrator::class.java)
        ?.takeIf { it.hasVibrator() }
        ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1))
}

private fun vibrateIncrement(context: Context) {
    context.getSystemService(Vibrator::class.java)
        ?.takeIf { it.hasVibrator() }
        ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1))
}

private fun vibrateConfirm(context: Context) {
    // Longer double-pulse than the stepper — same 4-segment structure known to work on Pixel 7.
    context.getSystemService(Vibrator::class.java)
        ?.takeIf { it.hasVibrator() }
        ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), intArrayOf(0, 80, 0, 80), -1))
}


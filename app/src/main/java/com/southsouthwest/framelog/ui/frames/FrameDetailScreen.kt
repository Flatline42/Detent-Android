package com.southsouthwest.framelog.ui.frames

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.ui.util.ExposureValues
import com.southsouthwest.framelog.ui.util.HorizontalScrollWheel
import com.southsouthwest.framelog.ui.util.WheelNotation
import com.southsouthwest.framelog.ui.util.wheelTierColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FrameDetailScreen(navController: NavHostController) {
    val viewModel: FrameDetailViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showLensPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Speech recognition: append recognized text into the note field
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        matches?.firstOrNull()?.let { viewModel.onNoteChanged(it) }
    }

    // Intercept system back when there are unsaved changes
    BackHandler(enabled = state.hasUnsavedChanges) {
        viewModel.onBackPressed()
    }

    // One-shot event handler
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                FrameDetailEvent.SaveSuccessful ->
                    navController.popBackStack()
                FrameDetailEvent.ConfirmDiscard ->
                    showDiscardDialog = true
                is FrameDetailEvent.OpenMapIntent -> {
                    // Open the system maps app at the recorded GPS coordinates
                    val uri = Uri.parse(
                        "geo:${event.lat},${event.lng}?q=${event.lat},${event.lng}"
                    )
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Derived display values
    // ---------------------------------------------------------------------------

    // Order filters: MRU chips first, then remaining roll filters
    val orderedFilters: List<Filter> = remember(state.mruFilterIds, state.rollFilters) {
        val mruFilters = state.mruFilterIds
            .mapNotNull { id -> state.rollFilters.firstOrNull { it.id == id } }
        val restFilters = state.rollFilters.filterNot { it.id in state.mruFilterIds }
        mruFilters + restFilters
    }

    // EV sum for active filters, with "~" prefix when any active filter has null evReduction
    val evSumDisplay: String? = remember(state.activeFilterIds, state.rollFilters) {
        val active = state.rollFilters.filter { it.id in state.activeFilterIds }
        if (active.isEmpty()) return@remember null
        val hasUnknownEv = active.any { it.evReduction == null }
        val sum = active.fold(0f) { acc, f -> acc + (f.evReduction ?: 0f) }
        val prefix = if (hasUnknownEv) "~" else ""
        "${prefix}${ExposureValues.formatExposureCompensation(sum)} EV"
    }

    // Shutter speed and aperture stepper indices
    val apertureIndex = state.availableApertures.indexOf(state.aperture)
    val shutterIndex = state.availableShutterSpeeds.indexOf(state.shutterSpeed)

    // EC stepper: treat null as pointing at the 0 EV position
    val ecValues = ExposureValues.exposureCompensationValues
    val ecZeroIndex = remember(ecValues) {
        ecValues.indexOfFirst { abs(it) < 0.001f }.takeIf { it >= 0 } ?: (ecValues.size / 2)
    }
    val ecIndex = state.exposureCompensation
        ?.let { v -> ecValues.indexOfFirst { abs(it - v) < 0.01f }.takeIf { it >= 0 } }
        ?: ecZeroIndex

    // Subtitle: timestamp + GPS indicator (film stock / body name not available in this VM)
    val dateTimeFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val subtitle: String? = remember(state.frame) {
        val frame = state.frame?.frame ?: return@remember null
        buildString {
            frame.loggedAt?.let { append(dateTimeFormatter.format(Date(it))) }
            if (frame.lat != null && frame.lng != null) {
                if (isNotEmpty()) append(" \u00b7 ")
                append("GPS logged")
            }
        }.ifBlank { null }
    }

    // ---------------------------------------------------------------------------
    // Scaffold
    // ---------------------------------------------------------------------------

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Frame ${state.frame?.frame?.frameNumber ?: ""}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.hasUnsavedChanges) viewModel.onBackPressed()
                            else navController.popBackStack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {

            // ==================================================================
            // Logged toggle
            // ==================================================================

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "logged",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                )
                FilterChip(
                    selected = state.isLogged,
                    onClick = { viewModel.onIsLoggedChanged(!state.isLogged) },
                    label = { Text(if (state.isLogged) "yes" else "no") },
                )
                if (state.isLogged) {
                    Text(
                        text = "tap to mark unlogged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()

            // ==================================================================
            // Lens
            // ==================================================================

            Spacer(Modifier.height(4.dp))

            // Lens picker row
            val selectedLensName = state.rollLenses
                .firstOrNull { it.id == state.selectedLensId }
                ?.name
                ?: if (state.rollLenses.isEmpty()) "No lenses on this roll" else "Select lens"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (state.rollLenses.isNotEmpty())
                            Modifier.clickable { showLensPicker = true }
                        else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "lens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    text = selectedLensName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.rollLenses.isNotEmpty()) {
                    Text(
                        text = "\u203a", // ›
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ==================================================================
            // Filters
            // ==================================================================

            if (state.rollFilters.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 0.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "filters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp).padding(top = 10.dp),
                    )
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        orderedFilters.forEach { filter ->
                            FilterChip(
                                selected = filter.id in state.activeFilterIds,
                                onClick = { viewModel.onFilterToggled(filter.id) },
                                label = { Text(filter.name) },
                            )
                        }
                    }
                    if (evSumDisplay != null) {
                        Text(
                            text = evSumDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, start = 8.dp),
                        )
                    }
                }
            }

            HorizontalDivider()

            // ==================================================================
            // Exposure compensation wheel (narrower, centered, recessed surround)
            // ==================================================================

            Spacer(Modifier.height(8.dp))
            Text(
                text = "exposure compensation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))

            run {
                val wheelColors = wheelTierColors()
                // ecValues runs -3.0 (index 0) → +3.0 (index last). No reversal.
                val ecIdxInWheel = ecIndex

                HorizontalScrollWheel(
                    values = ecValues.map { ExposureValues.formatExposureCompensation(it) },
                    selectedIndex = ecIdxInWheel,
                    onValueChange = { wheelIdx ->
                        viewModel.onExposureCompensationChanged(ecValues[wheelIdx])
                    },
                    labelFor = { idx, isCenter ->
                        val v = ecValues[idx]
                        val isWholeStop = (Math.round(v * 3) % 3) == 0
                        when {
                            isCenter -> ExposureValues.formatExposureCompensation(v)
                            isWholeStop -> ExposureValues.formatExposureCompensation(v)
                            else -> "·"
                        }
                    },
                    notation = WheelNotation.DOTS,
                    tierColors = wheelColors,
                    subLabelFor = { idx ->
                        val v = ecValues[idx]
                        val isWholeStop = (Math.round(v * 3) % 3) == 0
                        if (!isWholeStop) "%+.2f".format(v) else null
                    },
                    itemWidthDp = 56.dp,
                    wheelHeightDp = 52.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            // ==================================================================
            // Aperture wheel
            // ==================================================================

            Spacer(Modifier.height(12.dp))
            Text(
                text = "aperture",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))

            run {
                val apertureWheelReversed = remember {
                    AppPreferences(context).apertureWheelReversed
                }
                val wheelColors = wheelTierColors()
                val aperturesForWheel = remember(state.availableApertures, apertureWheelReversed) {
                    if (apertureWheelReversed) state.availableApertures.reversed()
                    else state.availableApertures
                }
                val apertureIdxInWheel = if (apertureWheelReversed)
                    state.availableApertures.size - 1 - apertureIndex
                else
                    apertureIndex

                // Subtle container behind aperture wheel — 10% of the selected tier color.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = wheelColors.selected.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(vertical = 4.dp),
                ) {
                    HorizontalScrollWheel(
                        values = aperturesForWheel,
                        selectedIndex = apertureIdxInWheel,
                        onValueChange = { wheelIdx ->
                            viewModel.onApertureChanged(aperturesForWheel[wheelIdx])
                        },
                        labelFor = { idx, isCenter ->
                            val stored = aperturesForWheel[idx]
                            val num = stored.removePrefix("f/")
                            when {
                                isCenter -> num
                                ExposureValues.isFullStopAperture(stored) -> num
                                else -> "·"
                            }
                        },
                        notation = WheelNotation.DOTS,
                        tierColors = wheelColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ==================================================================
            // Shutter speed wheel
            // ==================================================================

            Text(
                text = "shutter speed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))

            run {
                val wheelColors = wheelTierColors()
                val errorColor = MaterialTheme.colorScheme.error

                // Shutter list is slowest→fastest (index 0 = B).
                // Swipe right → lower index → slower/longer exposure. ✓
                // Subtle container — same 10% primary tint as aperture wheel.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = wheelColors.selected.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(vertical = 4.dp),
                ) {
                    HorizontalScrollWheel(
                        values = state.availableShutterSpeeds,
                        selectedIndex = shutterIndex,
                        onValueChange = { wheelIdx ->
                            viewModel.onShutterSpeedChanged(state.availableShutterSpeeds[wheelIdx])
                        },
                        labelFor = { idx, _ ->
                            ExposureValues.shutterDisplayValue(state.availableShutterSpeeds[idx])
                        },
                        notation = WheelNotation.PIPES,
                        tierColors = wheelColors,
                        selectedColorOverride = { idx ->
                            if (ExposureValues.isLongExposure(state.availableShutterSpeeds[idx])) errorColor
                            else null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()

            // ==================================================================
            // Note + mic
            // ==================================================================

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("note") },
                    minLines = 3,
                    maxLines = 5,
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Add a note for this frame")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            // Speech recognition not available on this device
                        }
                    },
                    modifier = Modifier
                        .height(88.dp)
                        .width(52.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = "Mic",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            HorizontalDivider()

            // ==================================================================
            // Meta: logged timestamp + GPS coordinates
            // ==================================================================

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                val loggedAtDisplay = state.frame?.frame?.loggedAt
                    ?.let { dateTimeFormatter.format(Date(it)) }
                if (loggedAtDisplay != null) {
                    Text(
                        text = "logged $loggedAtDisplay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val lat = state.frame?.frame?.lat
                val lng = state.frame?.frame?.lng
                if (lat != null && lng != null) {
                    val coordText = "%.4f, %.4f".format(lat, lng)
                    Text(
                        text = "$coordText  \u203a",
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDecoration = TextDecoration.Underline,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { viewModel.onGpsCoordinatesTapped() },
                    )
                }

                if (loggedAtDisplay == null && lat == null) {
                    Text(
                        text = "not yet logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // ==================================================================
            // Save button
            // ==================================================================

            Button(
                onClick = viewModel::onSaveTapped,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                enabled = !state.isSaving,
            ) {
                Text("Save Changes")
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ==========================================================================
    // Lens picker sheet
    // ==========================================================================

    if (showLensPicker) {
        ModalBottomSheet(
            onDismissRequest = { showLensPicker = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                text = "Select Lens",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(state.rollLenses, key = { it.id }) { lens ->
                    LensPickerItem(
                        lens = lens,
                        isSelected = lens.id == state.selectedLensId,
                        onClick = {
                            viewModel.onLensSelected(lens.id)
                            showLensPicker = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ==========================================================================
    // Discard changes dialog
    // ==========================================================================

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this frame will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        navController.popBackStack()
                    },
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Lens picker list item
// ---------------------------------------------------------------------------

@Composable
private fun LensPickerItem(
    lens: Lens,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(lens.name) },
        supportingContent = { Text("${lens.make} \u00b7 f/${lens.maxAperture}") },
        trailingContent = {
            if (isSelected) {
                Text(
                    text = "current",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

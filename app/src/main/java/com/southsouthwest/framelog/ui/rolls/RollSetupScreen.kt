package com.southsouthwest.framelog.ui.rolls

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.FilmStock
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.ui.navigation.KitSelector
import com.southsouthwest.framelog.ui.navigation.RollJournal
import com.southsouthwest.framelog.ui.navigation.RollSetup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RollSetupScreen(navController: NavHostController) {
    val viewModel: RollSetupViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Picker / dialog visibility
    var showFilmStockPicker by remember { mutableStateOf(false) }
    var showBodyPicker by remember { mutableStateOf(false) }
    var showLensPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showGpsDisabledDialog by remember { mutableStateOf(false) }
    // Custom ISO row is collapsed by default
    var showCustomIso by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RollSetupEvent.NavigateToKitSelector ->
                    navController.navigate(KitSelector)
                is RollSetupEvent.GpsDisabledInSettings ->
                    showGpsDisabledDialog = true
                is RollSetupEvent.RollCreated ->
                    navController.navigate(RollJournal(event.rollId)) {
                        popUpTo<RollSetup> { inclusive = true }
                    }
                is RollSetupEvent.RollCreatedAndLoaded ->
                    // TODO: navigate to QuickScreen once implemented
                    navController.navigate(RollJournal(event.rollId)) {
                        popUpTo<RollSetup> { inclusive = true }
                    }
                is RollSetupEvent.ShowErrorMessage ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Observe kit selection results written by KitSelectorScreen to the navigation back stack
    // SavedStateHandle. This must live in the composable because NavBackStackEntry.savedStateHandle
    // is a dedicated navigation-results handle — distinct from the ViewModel's own SavedStateHandle.
    // LaunchedEffect re-runs each time RollSetupScreen re-enters the composition (i.e. on return
    // from KitSelectorScreen), at which point the StateFlow immediately emits the stored kitId.
    LaunchedEffect(navController) {
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow("selected_kit_id", -1)
            ?.collect { kitId ->
                if (kitId != -1) {
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_kit_id", -1)
                    viewModel.loadAndApplyKit(kitId)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Roll") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {

            // ==================================================================
            // Film Stock
            // ==================================================================

            SectionHeader("Film Stock")

            // Film stock picker row
            PickerRow(
                label = "Film stock",
                value = state.selectedFilmStock?.let { "${it.make} ${it.name}" }
                    ?: "Select film stock",
                placeholder = state.selectedFilmStock == null,
                error = state.filmStockError,
                onClick = { showFilmStockPicker = true },
            )

            // Frame count + extra frames display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldLabel(text = "Frame count")
                OutlinedTextField(
                    value = state.frameCount.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let { viewModel.onFrameCountChanged(it) }
                    },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = "+ ${state.totalExposures - state.frameCount} extra = ${state.totalExposures} slots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Push / pull stepper
            PushPullRow(
                pushPull = state.pushPull,
                ratedISO = state.ratedISO,
                onDecrement = {
                    viewModel.onPushPullChanged((state.pushPull ?: 0) - 1)
                },
                onIncrement = {
                    viewModel.onPushPullChanged((state.pushPull ?: 0) + 1)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Custom ISO — expand/collapse link, directly below push/pull
            Text(
                text = if (showCustomIso) "use push/pull instead" else "set custom ISO instead of push/pull",
                style = MaterialTheme.typography.bodySmall.copy(
                    textDecoration = TextDecoration.Underline,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable {
                        showCustomIso = !showCustomIso
                        if (!showCustomIso) {
                            // Switching back to push/pull: restore pushPull = 0 if it was custom
                            if (state.pushPull == null) {
                                viewModel.onPushPullChanged(0)
                            }
                        }
                    },
            )

            if (showCustomIso) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    FieldLabel("Rated ISO")
                    OutlinedTextField(
                        value = state.customRatedIsoText,
                        onValueChange = viewModel::onCustomRatedIsoChanged,
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = {
                            Text(
                                text = state.ratedISO.toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            // Film expiry date (optional)
            val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
            PickerRow(
                label = "Film expiry date",
                value = state.filmExpiryDate
                    ?.let { dateFormatter.format(Date(it)) }
                    ?: "Optional",
                placeholder = state.filmExpiryDate == null,
                onClick = { showDatePicker = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ==================================================================
            // Kit — optional
            // ==================================================================

            SectionHeader("Kit — optional")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onLoadKitTapped() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Load from kit — pre-fills gear below",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ==================================================================
            // Camera Body
            // ==================================================================

            SectionHeader("Camera Body")

            PickerRow(
                label = "Camera body",
                value = state.selectedCameraBody?.let { "${it.make} ${it.name}" }
                    ?: "Select camera body",
                placeholder = state.selectedCameraBody == null,
                error = state.cameraBodyError,
                onClick = { showBodyPicker = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ==================================================================
            // Lenses
            // ==================================================================

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Lenses",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                val mountType = state.selectedCameraBody?.mountType
                if (mountType != null) {
                    Text(
                        text = mountType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Selected lens rows
            state.selectedLenses.forEach { row ->
                LensRow(
                    lensRow = row,
                    onPrimarySelected = { viewModel.onPrimaryLensChanged(row.lens.id) },
                    onRemoved = { viewModel.onLensRemoved(row.lens.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            // Lenses error
            val lensesError = state.lensesError
            if (lensesError != null) {
                Text(
                    text = lensesError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Add lens button
            TextButton(
                onClick = { showLensPicker = true },
                enabled = state.selectedCameraBody != null,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text("+ add another lens")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ==================================================================
            // Filters
            // ==================================================================

            SectionHeader("Filters")

            if (state.availableFilters.isEmpty()) {
                Text(
                    text = "No filters in library",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.availableFilters.forEach { filter ->
                        val selected = state.selectedFilters.any { it.id == filter.id }
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onFilterToggled(filter) },
                            label = { Text(filter.name) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ==================================================================
            // Details
            // ==================================================================

            SectionHeader("Details")

            // GPS toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("GPS capture", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Saves location for each logged frame",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.gpsEnabled,
                    onCheckedChange = viewModel::onGpsEnabledChanged,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Roll name
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                FieldLabel("Roll name")
                OutlinedTextField(
                    value = state.rollName,
                    onValueChange = viewModel::onRollNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "e.g. Kodak Ultramax — Mar '26",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ==================================================================
            // Action buttons
            // ==================================================================

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::onCreateRollTapped,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    Text("Create Roll")
                }
                Button(
                    onClick = viewModel::onCreateAndLoadRollTapped,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    Text("Create + Load")
                }
            }

            // Bottom breathing room for the scroll area
            Spacer(Modifier.height(16.dp))
        }
    }

    // ==========================================================================
    // GPS disabled dialog
    // ==========================================================================

    if (showGpsDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDisabledDialog = false },
            title = { Text("GPS not enabled") },
            text = { Text("GPS capture is turned off globally. Enable it in Settings \u203a Shooting Defaults first.") },
            confirmButton = {
                TextButton(onClick = { showGpsDisabledDialog = false }) { Text("OK") }
            },
        )
    }

    // ==========================================================================
    // Film stock picker
    // ==========================================================================

    if (showFilmStockPicker) {
        ModalBottomSheet(
            onDismissRequest = { showFilmStockPicker = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                text = "Select Film Stock",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(state.availableFilmStocks, key = { it.id }) { stock ->
                    FilmStockListItem(
                        stock = stock,
                        isSelected = state.selectedFilmStock?.id == stock.id,
                        onClick = {
                            viewModel.onFilmStockSelected(stock)
                            showFilmStockPicker = false
                        },
                    )
                }
                if (state.availableFilmStocks.isEmpty()) {
                    item {
                        Text(
                            text = "No film stocks in library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ==========================================================================
    // Camera body picker
    // ==========================================================================

    if (showBodyPicker) {
        ModalBottomSheet(
            onDismissRequest = { showBodyPicker = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                text = "Select Camera Body",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(state.availableBodies, key = { it.id }) { body ->
                    CameraBodyListItem(
                        body = body,
                        isSelected = state.selectedCameraBody?.id == body.id,
                        onClick = {
                            viewModel.onCameraBodySelected(body)
                            showBodyPicker = false
                        },
                    )
                }
                if (state.availableBodies.isEmpty()) {
                    item {
                        Text(
                            text = "No camera bodies in library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ==========================================================================
    // Lens add picker
    // ==========================================================================

    if (showLensPicker) {
        val selectedIds = state.selectedLenses.map { it.lens.id }.toSet()
        val unselectedLenses = state.availableLenses.filterNot { it.id in selectedIds }

        ModalBottomSheet(
            onDismissRequest = { showLensPicker = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Text(
                text = "Add Lens",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(unselectedLenses, key = { it.id }) { lens ->
                    LensListItem(
                        lens = lens,
                        onClick = {
                            viewModel.onLensAdded(lens)
                            showLensPicker = false
                        },
                    )
                }
                if (unselectedLenses.isEmpty()) {
                    item {
                        Text(
                            text = if (state.availableLenses.isEmpty())
                                "No lenses match this camera's mount type"
                            else
                                "All compatible lenses already added",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ==========================================================================
    // Film expiry date picker
    // ==========================================================================

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.filmExpiryDate,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onFilmExpiryDateChanged(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                // "Clear" removes the date; "Cancel" dismisses without change
                Row {
                    TextButton(onClick = {
                        viewModel.onFilmExpiryDateChanged(null)
                        showDatePicker = false
                    }) {
                        Text("Clear")
                    }
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ---------------------------------------------------------------------------
// Section helpers
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Tappable row used for film stock and camera body selection. */
@Composable
private fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    error: String? = null,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (placeholder)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Push / pull stepper
// ---------------------------------------------------------------------------

@Composable
private fun PushPullRow(
    pushPull: Int?,
    ratedISO: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canDecrement = pushPull != null && pushPull > -3
    val canIncrement = pushPull != null && pushPull < 3

    val stepperLabel = when (pushPull) {
        null -> "—"
        0 -> "box"
        else -> if (pushPull > 0) "+$pushPull" else "$pushPull"
    }
    val isoLabel = when (pushPull) {
        null -> "ISO $ratedISO · custom"
        0 -> "ISO $ratedISO · box speed"
        else -> if (pushPull > 0) "push $pushPull · ISO $ratedISO" else "pull ${abs(pushPull)} · ISO $ratedISO"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        FieldLabel("Push / pull")
        Spacer(Modifier.height(40.dp)) // ensure row has consistent height
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onDecrement,
            enabled = canDecrement,
            modifier = Modifier
                .height(36.dp)
                .width(44.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("\u2212", style = MaterialTheme.typography.titleSmall)
        }

        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stepperLabel,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }

        OutlinedButton(
            onClick = onIncrement,
            enabled = canIncrement,
            modifier = Modifier
                .height(36.dp)
                .width(44.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("+", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = isoLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Lens row (selected lens with primary radio + remove)
// ---------------------------------------------------------------------------

@Composable
private fun LensRow(
    lensRow: RollLensRow,
    onPrimarySelected: () -> Unit,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = lensRow.isPrimary,
            onClick = onPrimarySelected,
        )
        Text(
            text = buildString {
                append(lensRow.lens.name)
                if (lensRow.isPrimary) append(" \u00b7 primary")
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRemoved) {
            Icon(
                Icons.Default.Clear,
                contentDescription = "Remove ${lensRow.lens.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Picker list items (inside bottom sheets)
// ---------------------------------------------------------------------------

@Composable
private fun FilmStockListItem(
    stock: FilmStock,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text("${stock.make} ${stock.name}") },
        supportingContent = { Text("ISO ${stock.iso}") },
        trailingContent = {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected")
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CameraBodyListItem(
    body: CameraBody,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text("${body.make} ${body.name}") },
        supportingContent = { Text(body.mountType) },
        trailingContent = {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected")
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun LensListItem(
    lens: Lens,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(lens.name) },
        supportingContent = { Text("${lens.make} · f/${lens.maxAperture}") },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

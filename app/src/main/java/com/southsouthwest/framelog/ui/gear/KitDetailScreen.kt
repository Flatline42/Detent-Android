package com.southsouthwest.framelog.ui.gear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Lens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitDetailScreen(
    navController: NavController,
    vm: KitDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                KitDetailEvent.SaveSuccessful -> navController.popBackStack()
                KitDetailEvent.DeleteSuccessful -> navController.popBackStack()
                // Duplicate creates a new kit but doesn't return its id, so return to the list.
                KitDetailEvent.DuplicateSuccessful -> navController.popBackStack()
                KitDetailEvent.ConfirmDiscard -> showDiscardDialog = true
                is KitDetailEvent.IncompatibleLensesRemoved -> {
                    val count = event.removedCount
                    val msg = if (count == 1) "1 incompatible lens removed"
                              else "$count incompatible lenses removed"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            }
        }
    }

    val isEditMode = state.id != 0
    val title = if (isEditMode) "Edit Kit" else "New Kit"

    val navigateBack: () -> Unit = {
        if (!state.isDirty) navController.popBackStack()
        else vm.onBackPressed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode) {
                        TextButton(onClick = vm::onDuplicateTapped) {
                            Text("Duplicate")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete kit")
                        }
                    }
                    TextButton(
                        onClick = vm::onSaveTapped,
                        enabled = !state.isSaving,
                    ) {
                        Text("Save")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Kit name
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onNameChanged,
                label = { Text("Kit name *") },
                placeholder = { Text("e.g. Street kit") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Camera body picker
            CameraBodyPickerField(
                selectedBody = state.cameraBody,
                availableBodies = state.availableBodies,
                onSelected = vm::onCameraBodySelected,
                isError = state.cameraBodyError != null,
                errorMessage = state.cameraBodyError,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Lenses section
            Text("Lenses *", style = MaterialTheme.typography.titleSmall)
            if (state.lensesError != null) {
                Text(
                    state.lensesError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            }

            // Add lens dropdown — only shows lenses compatible with the selected body
            AddLensDropdown(
                availableLenses = state.availableLenses.filter { available ->
                    state.lenses.none { added -> added.lens.id == available.id }
                },
                cameraBodySelected = state.cameraBody != null,
                onLensAdded = vm::onLensAdded,
            )

            // Added lenses list with primary selector and remove button
            state.lenses.forEach { lensRow ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = lensRow.isPrimary,
                        onClick = { vm.onPrimaryLensChanged(lensRow.lens.id) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lensRow.lens.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${lensRow.lens.make} · ${lensRow.lens.focalLengthMm}mm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (lensRow.isPrimary) {
                        Text(
                            "Primary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = { vm.onLensRemoved(lensRow.lens.id) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Remove lens")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Filters section (optional — any filter can be toggled on or off)
            Text("Filters", style = MaterialTheme.typography.titleSmall)
            if (state.availableFilters.isEmpty()) {
                Text(
                    "No filters in your gear library yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                state.availableFilters.forEach { filter ->
                    FilterToggleRow(
                        filter = filter,
                        checked = state.filters.any { it.id == filter.id },
                        onToggle = { vm.onFilterToggled(filter) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Notes
            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::onNotesChanged,
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
    }

    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = { navController.popBackStack() }) {
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

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete kit?") },
            text = { Text("This kit will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.onDeleteConfirmed(); showDeleteDialog = false }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Private composables
// ---------------------------------------------------------------------------

/**
 * Read-only dropdown for selecting a camera body from the gear library.
 * Handles the null case (no body selected yet) by showing an empty text field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraBodyPickerField(
    selectedBody: CameraBody?,
    availableBodies: List<CameraBody>,
    onSelected: (CameraBody) -> Unit,
    isError: Boolean,
    errorMessage: String?,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = selectedBody?.let { "${it.make} ${it.name}" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Camera body *") },
            placeholder = { Text("Select a camera body") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = isError,
            supportingText = errorMessage?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (availableBodies.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No camera bodies in gear library") },
                    onClick = { expanded = false },
                    enabled = false,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            } else {
                availableBodies.forEach { body ->
                    DropdownMenuItem(
                        text = { Text("${body.make} ${body.name}") },
                        onClick = { onSelected(body); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

/**
 * Dropdown for adding a lens to the kit. Only shows lenses compatible with the selected
 * camera body's mount type that haven't already been added.
 *
 * Disabled when no camera body is selected, since available lenses depend on mount type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLensDropdown(
    availableLenses: List<Lens>,
    cameraBodySelected: Boolean,
    onLensAdded: (Lens) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Reset expansion state if body changes and clears available lenses
    LaunchedEffect(availableLenses) { if (availableLenses.isEmpty()) expanded = false }

    ExposedDropdownMenuBox(
        expanded = expanded && cameraBodySelected,
        onExpandedChange = { if (cameraBodySelected) expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            enabled = cameraBodySelected,
            label = { Text("Add lens") },
            placeholder = {
                Text(
                    if (!cameraBodySelected) "Select a camera body first"
                    else if (availableLenses.isEmpty()) "No compatible lenses available"
                    else "Tap to add a lens"
                )
            },
            trailingIcon = {
                if (cameraBodySelected) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(
            expanded = expanded && cameraBodySelected,
            onDismissRequest = { expanded = false },
        ) {
            if (availableLenses.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No compatible lenses available") },
                    onClick = { expanded = false },
                    enabled = false,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            } else {
                availableLenses.forEach { lens ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(lens.name)
                                Text(
                                    "${lens.make} · ${lens.focalLengthMm}mm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { onLensAdded(lens); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

/** Single row in the filters section with a checkbox and filter details. */
@Composable
private fun FilterToggleRow(
    filter: Filter,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(filter.name, style = MaterialTheme.typography.bodyLarge)
            val detail = buildString {
                append(filter.make)
                if (filter.filterType.isNotBlank()) append(" · ${filter.filterType}")
                if (filter.evReduction != null) append(" · ${filter.evReduction} EV")
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

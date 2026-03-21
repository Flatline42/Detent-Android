package com.southsouthwest.framelog.ui.gear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.southsouthwest.framelog.data.db.entity.ApertureIncrements

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensDetailScreen(
    navController: NavController,
    vm: LensDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                LensDetailEvent.SaveSuccessful -> navController.popBackStack()
                LensDetailEvent.DeleteSuccessful -> navController.popBackStack()
                LensDetailEvent.ConfirmDiscard -> showDiscardDialog = true
            }
        }
    }

    val isEditMode = state.id != 0
    val title = if (isEditMode) "Edit Lens" else "New Lens"

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
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete lens")
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
            // Name
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onNameChanged,
                label = { Text("Name *") },
                placeholder = { Text("e.g. 50mm f/1.4 Summilux") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Make
            OutlinedTextField(
                value = state.make,
                onValueChange = vm::onMakeChanged,
                label = { Text("Make *") },
                placeholder = { Text("e.g. Leitz") },
                isError = state.makeError != null,
                supportingText = state.makeError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Focal length
            OutlinedTextField(
                value = state.focalLengthMm,
                onValueChange = vm::onFocalLengthChanged,
                label = { Text("Focal length (mm) *") },
                placeholder = { Text("e.g. 50") },
                isError = state.focalLengthError != null,
                supportingText = state.focalLengthError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Mount type — autocomplete from existing lens mounts
            MountTypeField(
                value = state.mountType,
                onValueChange = vm::onMountTypeChanged,
                suggestions = state.mountTypeSuggestions,
                isError = state.mountTypeError != null,
                errorMessage = state.mountTypeError,
            )

            // Max aperture (widest — smallest f-number)
            OutlinedTextField(
                value = state.maxAperture,
                onValueChange = vm::onMaxApertureChanged,
                label = { Text("Maximum aperture *") },
                placeholder = { Text("e.g. 1.4") },
                prefix = { Text("f/") },
                isError = state.maxApertureError != null,
                supportingText = state.maxApertureError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Min aperture (narrowest — largest f-number)
            OutlinedTextField(
                value = state.minAperture,
                onValueChange = vm::onMinApertureChanged,
                label = { Text("Minimum aperture *") },
                placeholder = { Text("e.g. 16") },
                prefix = { Text("f/") },
                isError = state.minApertureError != null,
                supportingText = state.minApertureError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Aperture increments picker
            EnumDropdown(
                label = "Aperture increments *",
                options = ApertureIncrements.entries,
                selected = state.apertureIncrements,
                onSelected = vm::onApertureIncrementsChanged,
                displayName = { it.label },
            )

            // Filter size (optional)
            OutlinedTextField(
                value = state.filterSizeMm,
                onValueChange = vm::onFilterSizeChanged,
                label = { Text("Filter size (mm)") },
                placeholder = { Text("e.g. 49") },
                suffix = { Text("mm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

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
            title = { Text("Delete lens?") },
            text = { Text("This lens will be permanently deleted. This cannot be undone.") },
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
// Shared field composables — reused across gear detail screens
// ---------------------------------------------------------------------------

/**
 * Mount type text field with type-ahead autocomplete from existing lens mount types.
 * Suggestions are filtered as the user types. The user can enter any free-form value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MountTypeField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    isError: Boolean,
    errorMessage: String?,
    label: String = "Mount type *",
    modifier: Modifier = Modifier,
) {
    val filtered = remember(value, suggestions) {
        suggestions.filter { it.contains(value, ignoreCase = true) && it != value }
    }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(label) },
            placeholder = { Text("e.g. M-mount, EF, F") },
            isError = isError,
            supportingText = errorMessage?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, true),
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = { onValueChange(suggestion); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * Read-only dropdown picker for enum values.
 * Used for ApertureIncrements, ShutterIncrements, CameraBodyFormat, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    displayName: (T) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = displayName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayName(option)) },
                    onClick = { onSelected(option); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Enum display names used in gear detail screens
// ---------------------------------------------------------------------------

internal val ApertureIncrements.label: String
    get() = when (this) {
        ApertureIncrements.FULL -> "Full stops"
        ApertureIncrements.HALF -> "Half stops"
        ApertureIncrements.THIRD -> "Third stops"
    }

package com.southsouthwest.framelog.ui.gear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDetailScreen(
    navController: NavController,
    vm: FilterDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                FilterDetailEvent.SaveSuccessful -> navController.popBackStack()
                FilterDetailEvent.DeleteSuccessful -> navController.popBackStack()
                FilterDetailEvent.ConfirmDiscard -> showDiscardDialog = true
            }
        }
    }

    val isEditMode = state.id != 0
    val title = if (isEditMode) "Edit Filter" else "New Filter"

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
                            Icon(Icons.Default.Delete, contentDescription = "Delete filter")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Name
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onNameChanged,
                label = { Text("Name *") },
                placeholder = { Text("e.g. Yellow 8") },
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
                placeholder = { Text("e.g. Hoya") },
                isError = state.makeError != null,
                supportingText = state.makeError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Filter type — autocomplete from existing filter types (folksonomy)
            FilterTypeField(
                value = state.filterType,
                onValueChange = vm::onFilterTypeChanged,
                suggestions = state.filterTypeSuggestions,
                isError = state.filterTypeError != null,
                errorMessage = state.filterTypeError,
            )

            // EV reduction (optional — blank = null in DB, e.g. UV filter has no exposure effect)
            OutlinedTextField(
                value = state.evReduction,
                onValueChange = vm::onEvReductionChanged,
                label = { Text("EV reduction") },
                placeholder = { Text("e.g. 3.0") },
                isError = state.evReductionError != null,
                supportingText = state.evReductionError?.let { { Text(it) } }
                    ?: { Text("Leave blank if this filter has no light reduction (e.g. UV, clear)") },
                suffix = { Text("EV") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
            title = { Text("Delete filter?") },
            text = { Text("This filter will be permanently deleted. This cannot be undone.") },
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

/**
 * Filter type text field with type-ahead autocomplete from existing filter types.
 * Same pattern as [MountTypeField] — user can enter any free-form value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterTypeField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    isError: Boolean,
    errorMessage: String?,
) {
    val filtered = remember(value, suggestions) {
        suggestions.filter { it.contains(value, ignoreCase = true) && it != value }
    }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("Filter type *") },
            placeholder = { Text("e.g. Yellow, UV, ND, Polarizer") },
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

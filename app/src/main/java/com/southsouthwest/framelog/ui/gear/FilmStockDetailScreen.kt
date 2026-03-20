package com.southsouthwest.framelog.ui.gear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.southsouthwest.framelog.data.db.entity.ColorType
import com.southsouthwest.framelog.data.db.entity.FilmFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmStockDetailScreen(
    navController: NavController,
    vm: FilmStockDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                FilmStockDetailEvent.SaveSuccessful -> navController.popBackStack()
                FilmStockDetailEvent.DeleteSuccessful -> navController.popBackStack()
                FilmStockDetailEvent.ConfirmDiscard -> showDiscardDialog = true
            }
        }
    }

    val isEditMode = state.id != 0
    val title = if (isEditMode) "Edit Film Stock" else "New Film Stock"

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
                            Icon(Icons.Default.Delete, contentDescription = "Delete film stock")
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
                placeholder = { Text("e.g. HP5 Plus") },
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
                placeholder = { Text("e.g. Ilford") },
                isError = state.makeError != null,
                supportingText = state.makeError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // ISO (box speed — stored as positive integer)
            OutlinedTextField(
                value = state.iso,
                onValueChange = vm::onIsoChanged,
                label = { Text("ISO (box speed) *") },
                placeholder = { Text("e.g. 400") },
                isError = state.isoError != null,
                supportingText = state.isoError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Format — v1.0: 35mm only; MEDIUM_FORMAT excluded from UI
            EnumDropdown(
                label = "Format *",
                options = FilmFormat.entries.filter { it != FilmFormat.MEDIUM_FORMAT },
                selected = state.format,
                onSelected = vm::onFormatChanged,
                displayName = { it.label },
            )

            // Default frame count
            OutlinedTextField(
                value = state.defaultFrameCount,
                onValueChange = vm::onDefaultFrameCountChanged,
                label = { Text("Default frame count *") },
                placeholder = { Text("e.g. 36") },
                isError = state.defaultFrameCountError != null,
                supportingText = state.defaultFrameCountError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Color type
            EnumDropdown(
                label = "Type *",
                options = ColorType.entries,
                selected = state.colorType,
                onSelected = vm::onColorTypeChanged,
                displayName = { it.label },
            )

            // Discontinued toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Discontinued", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Mark stocks that are no longer in production",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.discontinued,
                    onCheckedChange = vm::onDiscontinuedChanged,
                )
            }

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
            title = { Text("Delete film stock?") },
            text = { Text("This film stock will be permanently deleted. This cannot be undone.") },
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
// Enum display labels for film stock fields
// ---------------------------------------------------------------------------

internal val FilmFormat.label: String
    get() = when (this) {
        FilmFormat.THIRTY_FIVE_MM -> "35mm"
        FilmFormat.MEDIUM_FORMAT -> "Medium format" // reserved — not shown in v1.0 UI
    }

// ColorType.label is defined in GearLibraryScreen.kt (internal, same package)

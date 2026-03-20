package com.southsouthwest.framelog.ui.gear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.southsouthwest.framelog.data.db.entity.CameraBodyFormat
import com.southsouthwest.framelog.data.db.entity.ShutterIncrements

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraBodyDetailScreen(
    navController: NavController,
    vm: CameraBodyDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                CameraBodyDetailEvent.SaveSuccessful -> navController.popBackStack()
                CameraBodyDetailEvent.DeleteSuccessful -> navController.popBackStack()
                CameraBodyDetailEvent.ConfirmDiscard -> showDiscardDialog = true
            }
        }
    }

    val isEditMode = state.id != 0
    val title = if (isEditMode) "Edit Camera Body" else "New Camera Body"

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
                            Icon(Icons.Default.Delete, contentDescription = "Delete camera body")
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
                placeholder = { Text("e.g. M6 TTL") },
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
                placeholder = { Text("e.g. Leica") },
                isError = state.makeError != null,
                supportingText = state.makeError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Model
            OutlinedTextField(
                value = state.model,
                onValueChange = vm::onModelChanged,
                label = { Text("Model *") },
                placeholder = { Text("e.g. M6 TTL 0.72") },
                isError = state.modelError != null,
                supportingText = state.modelError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            // Mount type — autocomplete from existing lens mount types
            MountTypeField(
                value = state.mountType,
                onValueChange = vm::onMountTypeChanged,
                suggestions = state.mountTypeSuggestions,
                isError = state.mountTypeError != null,
                errorMessage = state.mountTypeError,
            )

            // Format — v1.0: 35mm and half frame only; MEDIUM_FORMAT excluded from UI
            EnumDropdown(
                label = "Format *",
                options = CameraBodyFormat.entries.filter { it != CameraBodyFormat.MEDIUM_FORMAT },
                selected = state.format,
                onSelected = vm::onFormatChanged,
                displayName = { it.label },
            )

            // Shutter speed increments — determines which shutter values appear in the stepper
            EnumDropdown(
                label = "Shutter increments *",
                options = ShutterIncrements.entries,
                selected = state.shutterIncrements,
                onSelected = vm::onShutterIncrementsChanged,
                displayName = { it.label },
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
            title = { Text("Delete camera body?") },
            text = { Text("This camera body will be permanently deleted. This cannot be undone.") },
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
// Enum display labels for camera body fields
// ---------------------------------------------------------------------------

internal val CameraBodyFormat.label: String
    get() = when (this) {
        CameraBodyFormat.THIRTY_FIVE_MM -> "35mm"
        CameraBodyFormat.HALF_FRAME -> "Half frame"
        CameraBodyFormat.MEDIUM_FORMAT -> "Medium format" // reserved — not shown in v1.0 UI
    }

internal val ShutterIncrements.label: String
    get() = when (this) {
        ShutterIncrements.FULL -> "Full stops"
        ShutterIncrements.HALF -> "Half stops"
        ShutterIncrements.THIRD -> "Third stops"
    }

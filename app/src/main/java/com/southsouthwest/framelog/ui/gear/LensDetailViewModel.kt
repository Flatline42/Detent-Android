package com.southsouthwest.framelog.ui.gear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.ApertureIncrements
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.data.repository.GearRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UiState
// ---------------------------------------------------------------------------

/**
 * Editable form state for the Lens Detail/Edit screen.
 *
 * When [id] == 0 this is a new lens (Create mode). Non-zero means Edit mode.
 * [isDirty] gates the unsaved-changes back-press prompt.
 */
data class LensDetailUiState(
    val id: Int = 0,
    val name: String = "",
    val make: String = "",
    val focalLengthMm: String = "", // String for the text field; validated on save
    val mountType: String = "",
    val maxAperture: String = "", // "f/" prefix stripped in the input field
    val minAperture: String = "",
    val apertureIncrements: ApertureIncrements = ApertureIncrements.THIRD,
    val filterSizeMm: String = "", // blank = null in DB
    val notes: String = "",
    // Autocomplete suggestions for the folksonomy mount type field
    val mountTypeSuggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    // Inline validation errors — null means valid
    val nameError: String? = null,
    val makeError: String? = null,
    val focalLengthError: String? = null,
    val mountTypeError: String? = null,
    val maxApertureError: String? = null,
    val minApertureError: String? = null,
)

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

sealed class LensDetailEvent {
    data object SaveSuccessful : LensDetailEvent()
    data object DeleteSuccessful : LensDetailEvent()
    /** User pressed back with unsaved changes — prompt them before discarding. */
    data object ConfirmDiscard : LensDetailEvent()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class LensDetailViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    /** 0 = new lens, non-zero = edit existing lens. Passed via nav arg key "id". */
    private val lensId: Int = savedState["id"] ?: 0

    private val gearRepository = GearRepository(AppDatabase.getInstance(application))

    private val _state = MutableStateFlow(LensDetailUiState(id = lensId, isLoading = lensId != 0))
    val state: StateFlow<LensDetailUiState> = _state.asStateFlow()

    private val _events = Channel<LensDetailEvent>(Channel.BUFFERED)
    val events: Flow<LensDetailEvent> = _events.receiveAsFlow()

    init {
        loadMountTypeSuggestions()
        if (lensId != 0) loadExistingLens()
    }

    private fun loadMountTypeSuggestions() = viewModelScope.launch {
        gearRepository.getDistinctMountTypes().collect { types ->
            _state.update { it.copy(mountTypeSuggestions = types) }
        }
    }

    private fun loadExistingLens() = viewModelScope.launch {
        // take(1): populate form once from DB, then leave it editable without
        // subsequent DB updates overwriting the user's in-progress edits.
        gearRepository.getLensById(lensId).collect { lens ->
            _state.update {
                it.copy(
                    name = lens.name,
                    make = lens.make,
                    focalLengthMm = lens.focalLengthMm.toString(),
                    mountType = lens.mountType,
                    maxAperture = lens.maxAperture.toString(),
                    minAperture = lens.minAperture.toString(),
                    apertureIncrements = lens.apertureIncrements,
                    filterSizeMm = lens.filterSizeMm?.toString() ?: "",
                    notes = lens.notes ?: "",
                    isLoading = false,
                    isDirty = false,
                )
            }
            // Stop collecting after the first value
            return@collect
        }
    }

    // ---------------------------------------------------------------------------
    // Field updates — each marks the form dirty
    // ---------------------------------------------------------------------------

    fun onNameChanged(value: String) =
        _state.update { it.copy(name = value, isDirty = true, nameError = null) }

    fun onMakeChanged(value: String) =
        _state.update { it.copy(make = value, isDirty = true, makeError = null) }

    fun onFocalLengthChanged(value: String) =
        _state.update { it.copy(focalLengthMm = value, isDirty = true, focalLengthError = null) }

    fun onMountTypeChanged(value: String) =
        _state.update { it.copy(mountType = value, isDirty = true, mountTypeError = null) }

    fun onMaxApertureChanged(value: String) =
        _state.update { it.copy(maxAperture = value, isDirty = true, maxApertureError = null) }

    fun onMinApertureChanged(value: String) =
        _state.update { it.copy(minAperture = value, isDirty = true, minApertureError = null) }

    fun onApertureIncrementsChanged(value: ApertureIncrements) =
        _state.update { it.copy(apertureIncrements = value, isDirty = true) }

    fun onFilterSizeChanged(value: String) =
        _state.update { it.copy(filterSizeMm = value, isDirty = true) }

    fun onNotesChanged(value: String) =
        _state.update { it.copy(notes = value, isDirty = true) }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    fun onSaveTapped() = viewModelScope.launch {
        val s = _state.value
        if (!validate(s)) return@launch

        _state.update { it.copy(isSaving = true) }

        val lens = Lens(
            id = lensId,
            name = s.name.trim(),
            make = s.make.trim(),
            focalLengthMm = s.focalLengthMm.toInt(),
            mountType = s.mountType.trim(),
            maxAperture = s.maxAperture.toFloat(),
            minAperture = s.minAperture.toFloat(),
            apertureIncrements = s.apertureIncrements,
            filterSizeMm = s.filterSizeMm.toIntOrNull(),
            notes = s.notes.trim().ifBlank { null },
        )

        if (lensId == 0) {
            gearRepository.insertLens(lens)
        } else {
            gearRepository.updateLens(lens)
        }

        _state.update { it.copy(isSaving = false, isDirty = false) }
        _events.send(LensDetailEvent.SaveSuccessful)
    }

    // ---------------------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------------------

    fun onDeleteConfirmed() = viewModelScope.launch {
        val s = _state.value
        if (lensId == 0) return@launch // shouldn't happen — delete only shown in edit mode
        val lens = Lens(
            id = lensId,
            name = s.name,
            make = s.make,
            focalLengthMm = s.focalLengthMm.toIntOrNull() ?: 0,
            mountType = s.mountType,
            maxAperture = s.maxAperture.toFloatOrNull() ?: 0f,
            minAperture = s.minAperture.toFloatOrNull() ?: 0f,
            apertureIncrements = s.apertureIncrements,
        )
        gearRepository.deleteLens(lens)
        _events.send(LensDetailEvent.DeleteSuccessful)
    }

    // ---------------------------------------------------------------------------
    // Back press
    // ---------------------------------------------------------------------------

    fun onBackPressed() = viewModelScope.launch {
        if (_state.value.isDirty) {
            _events.send(LensDetailEvent.ConfirmDiscard)
        }
        // If not dirty, the UI navigates back directly without asking the ViewModel.
    }

    // ---------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------

    private fun validate(s: LensDetailUiState): Boolean {
        var valid = true

        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            valid = false
        }
        if (s.make.isBlank()) {
            _state.update { it.copy(makeError = "Make is required") }
            valid = false
        }
        if (s.focalLengthMm.toIntOrNull() == null || s.focalLengthMm.toInt() <= 0) {
            _state.update { it.copy(focalLengthError = "Enter a valid focal length in mm") }
            valid = false
        }
        if (s.mountType.isBlank()) {
            _state.update { it.copy(mountTypeError = "Mount type is required") }
            valid = false
        }
        val maxAp = s.maxAperture.toFloatOrNull()
        if (maxAp == null || maxAp <= 0f) {
            _state.update { it.copy(maxApertureError = "Enter a valid maximum aperture") }
            valid = false
        }
        val minAp = s.minAperture.toFloatOrNull()
        if (minAp == null || minAp <= 0f) {
            _state.update { it.copy(minApertureError = "Enter a valid minimum aperture") }
            valid = false
        }
        if (maxAp != null && minAp != null && maxAp > minAp) {
            _state.update { it.copy(maxApertureError = "Max aperture must be ≤ min aperture (wider = smaller f-number)") }
            valid = false
        }
        return valid
    }
}

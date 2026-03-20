package com.southsouthwest.framelog.ui.gear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Kit
import com.southsouthwest.framelog.data.db.entity.KitFilter
import com.southsouthwest.framelog.data.db.entity.KitLens
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.data.repository.GearRepository
import com.southsouthwest.framelog.data.repository.KitRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UiState
// ---------------------------------------------------------------------------

/** A lens row in the kit with its primary designation. */
data class KitLensRow(
    val lens: Lens,
    val isPrimary: Boolean,
)

data class KitDetailUiState(
    val id: Int = 0,
    val name: String = "",
    val cameraBody: CameraBody? = null,
    val lenses: List<KitLensRow> = emptyList(),
    val filters: List<Filter> = emptyList(),
    val notes: String = "",
    // Available items for pickers (populated once cameraBody is chosen)
    val availableBodies: List<CameraBody> = emptyList(),
    val availableLenses: List<Lens> = emptyList(), // filtered by cameraBody.mountType
    val availableFilters: List<Filter> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val nameError: String? = null,
    val cameraBodyError: String? = null,
    val lensesError: String? = null,
)

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

sealed class KitDetailEvent {
    data object SaveSuccessful : KitDetailEvent()
    data object DeleteSuccessful : KitDetailEvent()
    data object DuplicateSuccessful : KitDetailEvent()
    data object ConfirmDiscard : KitDetailEvent()
    /**
     * Emitted when the camera body is changed to one with a different mount type, and
     * one or more lenses were removed because they are incompatible.
     * The UI should show the warning from the DESIGN decisions doc.
     */
    data class IncompatibleLensesRemoved(val removedCount: Int) : KitDetailEvent()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class KitDetailViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val kitId: Int = savedState["id"] ?: 0
    private val db = AppDatabase.getInstance(application)
    private val gearRepository = GearRepository(db)
    private val kitRepository = KitRepository(db)

    private val _state = MutableStateFlow(KitDetailUiState(id = kitId, isLoading = kitId != 0))
    val state: StateFlow<KitDetailUiState> = _state.asStateFlow()

    private val _events = Channel<KitDetailEvent>(Channel.BUFFERED)
    val events: Flow<KitDetailEvent> = _events.receiveAsFlow()

    init {
        loadAvailableBodies()
        loadAvailableFilters()
        if (kitId != 0) loadExistingKit()
    }

    private fun loadAvailableBodies() = viewModelScope.launch {
        gearRepository.searchCameraBodies("").collect { bodies ->
            _state.update { it.copy(availableBodies = bodies) }
        }
    }

    private fun loadAvailableFilters() = viewModelScope.launch {
        gearRepository.searchFilters("").collect { filters ->
            _state.update { it.copy(availableFilters = filters) }
        }
    }

    private fun loadExistingKit() = viewModelScope.launch {
        kitRepository.getKitWithDetails(kitId).collect { kitWithDetails ->
            val lensRows = kitWithDetails.lenses.map { kl ->
                KitLensRow(lens = kl.lens, isPrimary = kl.kitLens.isPrimary)
            }
            // Load available lenses for this kit's mount type
            val mountType = kitWithDetails.cameraBody.mountType
            val compatibleLenses = gearRepository.getLensesByMountType(mountType).first()

            _state.update {
                it.copy(
                    name = kitWithDetails.kit.name,
                    cameraBody = kitWithDetails.cameraBody,
                    lenses = lensRows,
                    filters = kitWithDetails.filters.map { kf -> kf.filter },
                    notes = kitWithDetails.kit.notes ?: "",
                    availableLenses = compatibleLenses,
                    isLoading = false,
                    isDirty = false,
                )
            }
            return@collect
        }
    }

    // ---------------------------------------------------------------------------
    // Field updates
    // ---------------------------------------------------------------------------

    fun onNameChanged(value: String) =
        _state.update { it.copy(name = value, isDirty = true, nameError = null) }

    fun onNotesChanged(value: String) =
        _state.update { it.copy(notes = value, isDirty = true) }

    /**
     * Changes the camera body. Removes any lenses that are incompatible with the new
     * body's mount type. Emits [KitDetailEvent.IncompatibleLensesRemoved] if any were removed
     * so the UI can show the warning from the DESIGN decisions doc.
     */
    fun onCameraBodySelected(body: CameraBody) = viewModelScope.launch {
        val currentLenses = _state.value.lenses
        val compatibleLenses = gearRepository.getLensesByMountType(body.mountType).first()
        val compatibleIds = compatibleLenses.map { it.id }.toSet()

        val remainingLenses = currentLenses.filter { it.lens.id in compatibleIds }
        val removedCount = currentLenses.size - remainingLenses.size

        // If the primary lens was removed, auto-promote the first remaining lens
        val hasPrimary = remainingLenses.any { it.isPrimary }
        val adjustedLenses = if (!hasPrimary && remainingLenses.isNotEmpty()) {
            remainingLenses.toMutableList().also { list ->
                list[0] = list[0].copy(isPrimary = true)
            }
        } else {
            remainingLenses
        }

        _state.update {
            it.copy(
                cameraBody = body,
                lenses = adjustedLenses,
                availableLenses = compatibleLenses,
                isDirty = true,
                cameraBodyError = null,
            )
        }

        if (removedCount > 0) {
            _events.send(KitDetailEvent.IncompatibleLensesRemoved(removedCount))
        }
    }

    fun onLensAdded(lens: Lens) {
        val current = _state.value.lenses
        if (current.any { it.lens.id == lens.id }) return // already added
        val isFirst = current.isEmpty()
        _state.update {
            it.copy(
                lenses = current + KitLensRow(lens = lens, isPrimary = isFirst),
                isDirty = true,
                lensesError = null,
            )
        }
    }

    fun onLensRemoved(lensId: Int) {
        val current = _state.value.lenses.toMutableList()
        val removedWasPrimary = current.find { it.lens.id == lensId }?.isPrimary == true
        current.removeAll { it.lens.id == lensId }

        // If the primary was removed, auto-promote the first remaining lens
        val adjusted = if (removedWasPrimary && current.isNotEmpty()) {
            current.also { it[0] = it[0].copy(isPrimary = true) }
        } else {
            current
        }
        _state.update { it.copy(lenses = adjusted, isDirty = true) }
    }

    fun onPrimaryLensChanged(lensId: Int) {
        _state.update {
            it.copy(
                lenses = it.lenses.map { row ->
                    row.copy(isPrimary = row.lens.id == lensId)
                },
                isDirty = true,
            )
        }
    }

    fun onFilterToggled(filter: Filter) {
        val current = _state.value.filters
        val updated = if (current.any { it.id == filter.id }) {
            current.filterNot { it.id == filter.id }
        } else {
            current + filter
        }
        _state.update { it.copy(filters = updated, isDirty = true) }
    }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    fun onSaveTapped() = viewModelScope.launch {
        val s = _state.value
        if (!validate(s)) return@launch

        _state.update { it.copy(isSaving = true) }

        val kit = Kit(
            id = kitId,
            name = s.name.trim(),
            cameraBodyId = s.cameraBody!!.id,
            lastUsedAt = if (kitId == 0) null else
                kitRepository.getKitById(kitId).first().lastUsedAt,
            notes = s.notes.trim().ifBlank { null },
        )

        val kitLenses = s.lenses.mapIndexed { _, row ->
            KitLens(kitId = kitId, lensId = row.lens.id, isPrimary = row.isPrimary)
        }
        val kitFilters = s.filters.map { filter ->
            KitFilter(kitId = kitId, filterId = filter.id)
        }

        kitRepository.saveKitWithAssociations(kit, kitLenses, kitFilters)
        _state.update { it.copy(isSaving = false, isDirty = false) }
        _events.send(KitDetailEvent.SaveSuccessful)
    }

    // ---------------------------------------------------------------------------
    // Delete / Duplicate
    // ---------------------------------------------------------------------------

    fun onDeleteConfirmed() = viewModelScope.launch {
        if (kitId == 0) return@launch
        val kit = Kit(id = kitId, name = _state.value.name, cameraBodyId = _state.value.cameraBody?.id ?: 0)
        kitRepository.deleteKit(kit)
        _events.send(KitDetailEvent.DeleteSuccessful)
    }

    /**
     * Creates a duplicate of this kit with "Copy of [name]" as the default name.
     * Opens the new kit for immediate editing via [KitDetailEvent.DuplicateSuccessful].
     */
    fun onDuplicateTapped() = viewModelScope.launch {
        val s = _state.value
        if (kitId == 0 || s.cameraBody == null) return@launch

        val copyKit = Kit(
            id = 0,
            name = "Copy of ${s.name.trim()}",
            cameraBodyId = s.cameraBody.id,
        )
        val copyLenses = s.lenses.map { row ->
            KitLens(kitId = 0, lensId = row.lens.id, isPrimary = row.isPrimary)
        }
        val copyFilters = s.filters.map { filter ->
            KitFilter(kitId = 0, filterId = filter.id)
        }
        kitRepository.saveKitWithAssociations(copyKit, copyLenses, copyFilters)
        _events.send(KitDetailEvent.DuplicateSuccessful)
    }

    fun onBackPressed() = viewModelScope.launch {
        if (_state.value.isDirty) _events.send(KitDetailEvent.ConfirmDiscard)
    }

    // ---------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------

    private fun validate(s: KitDetailUiState): Boolean {
        var valid = true
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Kit name is required") }
            valid = false
        }
        if (s.cameraBody == null) {
            _state.update { it.copy(cameraBodyError = "Select a camera body") }
            valid = false
        }
        if (s.lenses.isEmpty()) {
            _state.update { it.copy(lensesError = "At least one lens is required") }
            valid = false
        }
        return valid
    }
}

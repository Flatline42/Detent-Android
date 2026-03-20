package com.southsouthwest.framelog.ui.frames

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.FrameFilter
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.data.db.relation.FrameWithDetails
import com.southsouthwest.framelog.data.repository.FrameRepository
import com.southsouthwest.framelog.data.repository.RollRepository
import com.southsouthwest.framelog.ui.util.ExposureValues
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

data class FrameDetailUiState(
    val frame: FrameWithDetails? = null,
    // Lenses available on this roll (for the lens picker)
    val rollLenses: List<Lens> = emptyList(),
    // Filters available on this roll (for the filter picker and chips)
    val rollFilters: List<Filter> = emptyList(),
    // Valid aperture values for the selected lens (drives the stepper)
    val availableApertures: List<String> = emptyList(),
    // Valid shutter speeds for the roll's camera body (drives the stepper)
    val availableShutterSpeeds: List<String> = emptyList(),

    // Editable draft — initialized from frame.frame on first load
    val isLogged: Boolean = false,
    val selectedLensId: Int? = null,
    val activeFilterIds: Set<Int> = emptySet(),
    /** MRU filter chip IDs (up to 4), ordered most-recently-toggled first. */
    val mruFilterIds: List<Int> = emptyList(),
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val exposureCompensation: Float? = null,
    val note: String = "",

    val hasUnsavedChanges: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
)

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

sealed class FrameDetailEvent {
    data object SaveSuccessful : FrameDetailEvent()
    /** User pressed back with unsaved changes. */
    data object ConfirmDiscard : FrameDetailEvent()
    /** GPS coordinates tapped — UI should open a geo Intent. */
    data class OpenMapIntent(val lat: Double, val lng: Double) : FrameDetailEvent()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class FrameDetailViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val frameId: Int = checkNotNull(savedState["frameId"]) {
        "FrameDetailViewModel requires a 'frameId' nav argument"
    }

    private val db = AppDatabase.getInstance(application)
    private val frameRepository = FrameRepository(db)
    private val rollRepository = RollRepository(db)
    private val appPreferences = AppPreferences(application)

    private val _state = MutableStateFlow(FrameDetailUiState())
    val state: StateFlow<FrameDetailUiState> = _state.asStateFlow()

    private val _events = Channel<FrameDetailEvent>(Channel.BUFFERED)
    val events: Flow<FrameDetailEvent> = _events.receiveAsFlow()

    // Track whether we've done the initial population so subsequent DB updates
    // (e.g. another device, edge cases) don't overwrite the user's in-progress edits.
    private var hasLoadedInitialData = false

    init {
        loadFrame()
    }

    private fun loadFrame() = viewModelScope.launch {
        frameRepository.getFrameById(frameId).collect { frameWithDetails ->
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                populateFromFrame(frameWithDetails)
            }
            // Always update the frame reference so lens/filter lists stay fresh
            // (new lenses added to the roll would appear without re-launching).
            _state.update { it.copy(frame = frameWithDetails) }
        }
    }

    private suspend fun populateFromFrame(frameWithDetails: FrameWithDetails) {
        val frame = frameWithDetails.frame
        val roll = rollRepository.getRollById(frame.rollId).first()

        // Build the lens list and shutter speed list from the roll's camera body
        val rollLenses = roll.lenses.map { it.lens }
        val cameraBody = null // We need to fetch the body from GearRepository
        // The roll entity has cameraBodyId but not the full CameraBody object.
        // RollWithDetails doesn't embed CameraBody — access via the roll's cameraBodyId.
        // Load shutter speeds from the body via DB.
        val db = AppDatabase.getInstance(getApplication())
        val body = db.cameraBodyDao().getCameraBodyById(roll.roll.cameraBodyId).first()
        val shutterSpeeds = ExposureValues.shutterSpeeds(body.shutterIncrements)
        val rollFilters = roll.filters.map { it.filter }

        // Determine available apertures for the initially selected lens
        val selectedLens = rollLenses.firstOrNull { it.id == frame.lensId }
        val apertures = selectedLens?.let {
            ExposureValues.apertures(it.maxAperture, it.minAperture, it.apertureIncrements)
        } ?: emptyList()

        // Restore MRU filter list from SharedPreferences
        val mruIds = appPreferences.getMruFilterIds(frame.rollId)
            .filter { id -> rollFilters.any { it.id == id } }

        val existingFilterIds = frameWithDetails.filters.map { it.id }.toSet()

        _state.update {
            it.copy(
                frame = frameWithDetails,
                rollLenses = rollLenses,
                rollFilters = rollFilters,
                availableShutterSpeeds = shutterSpeeds,
                availableApertures = apertures,
                isLogged = frame.isLogged,
                selectedLensId = frame.lensId,
                activeFilterIds = existingFilterIds,
                mruFilterIds = mruIds,
                aperture = frame.aperture,
                shutterSpeed = frame.shutterSpeed,
                exposureCompensation = frame.exposureCompensation,
                note = frame.notes ?: "",
                hasUnsavedChanges = false,
                isLoading = false,
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Field updates
    // ---------------------------------------------------------------------------

    fun onIsLoggedChanged(logged: Boolean) =
        _state.update { it.copy(isLogged = logged, hasUnsavedChanges = true) }

    fun onLensSelected(lensId: Int) {
        val lens = _state.value.rollLenses.firstOrNull { it.id == lensId } ?: return
        val apertures = ExposureValues.apertures(lens.maxAperture, lens.minAperture, lens.apertureIncrements)
        // If current aperture is no longer valid for the new lens, snap to nearest available value
        val currentAperture = _state.value.aperture
        val adjustedAperture = if (currentAperture in apertures) currentAperture
        else apertures.firstOrNull()

        _state.update {
            it.copy(
                selectedLensId = lensId,
                availableApertures = apertures,
                aperture = adjustedAperture,
                hasUnsavedChanges = true,
            )
        }
    }

    fun onFilterToggled(filterId: Int) {
        val current = _state.value.activeFilterIds.toMutableSet()
        if (filterId in current) current.remove(filterId) else current.add(filterId)

        // Promote this filter to the front of the MRU list
        appPreferences.promoteFilterInMru(_state.value.frame?.frame?.rollId ?: return, filterId)
        val updatedMru = appPreferences.getMruFilterIds(_state.value.frame!!.frame.rollId)
            .filter { id -> _state.value.rollFilters.any { it.id == id } }

        _state.update {
            it.copy(
                activeFilterIds = current,
                mruFilterIds = updatedMru,
                hasUnsavedChanges = true,
            )
        }
    }

    fun onApertureChanged(aperture: String) =
        _state.update { it.copy(aperture = aperture, hasUnsavedChanges = true) }

    fun onShutterSpeedChanged(shutterSpeed: String) =
        _state.update { it.copy(shutterSpeed = shutterSpeed, hasUnsavedChanges = true) }

    fun onExposureCompensationChanged(ec: Float?) =
        _state.update { it.copy(exposureCompensation = ec, hasUnsavedChanges = true) }

    fun onNoteChanged(note: String) =
        _state.update { it.copy(note = note, hasUnsavedChanges = true) }

    // ---------------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------------

    fun onSaveTapped() = viewModelScope.launch {
        val s = _state.value
        val existingFrame = s.frame?.frame ?: return@launch

        _state.update { it.copy(isSaving = true) }

        val updatedFrame = Frame(
            id = existingFrame.id,
            rollId = existingFrame.rollId,
            frameNumber = existingFrame.frameNumber, // immutable
            isLogged = s.isLogged,
            // Preserve loggedAt: if newly marking as logged and no timestamp exists, set now.
            // If unmarking as logged, clear the timestamp.
            // If was already logged, keep original timestamp (retroactive correction).
            loggedAt = when {
                !s.isLogged -> null
                existingFrame.loggedAt != null -> existingFrame.loggedAt
                else -> System.currentTimeMillis()
            },
            aperture = s.aperture,
            shutterSpeed = s.shutterSpeed,
            lensId = s.selectedLensId,
            exposureCompensation = s.exposureCompensation,
            lat = existingFrame.lat, // GPS not editable on Frame Detail — captured on log
            lng = existingFrame.lng,
            notes = s.note.trim().ifBlank { null },
        )

        // Compute filter delta: add new, remove de-selected
        val previousFilterIds = s.frame.filters.map { it.id }.toSet()
        val currentFilterIds = s.activeFilterIds

        val filtersToAdd = (currentFilterIds - previousFilterIds).map { filterId ->
            FrameFilter(frameId = existingFrame.id, filterId = filterId)
        }
        val filterIdsToRemove = (previousFilterIds - currentFilterIds).toList()

        frameRepository.logFrame(updatedFrame, filtersToAdd, filterIdsToRemove)

        _state.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
        _events.send(FrameDetailEvent.SaveSuccessful)
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------

    fun onBackPressed() = viewModelScope.launch {
        if (_state.value.hasUnsavedChanges) {
            _events.send(FrameDetailEvent.ConfirmDiscard)
        }
    }

    fun onGpsCoordinatesTapped() = viewModelScope.launch {
        val frame = _state.value.frame?.frame ?: return@launch
        val lat = frame.lat ?: return@launch
        val lng = frame.lng ?: return@launch
        _events.send(FrameDetailEvent.OpenMapIntent(lat, lng))
    }
}

package com.southsouthwest.framelog.ui.quickscreen

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.R
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.FrameFilter
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.data.db.relation.RollWithDetails
import com.southsouthwest.framelog.data.repository.FrameRepository
import com.southsouthwest.framelog.data.repository.RollRepository
import com.southsouthwest.framelog.ui.util.ExposureValues
import com.southsouthwest.framelog.ui.widget.FrameLogWidgetUpdater
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

data class QuickScreenUiState(
    /** All rolls currently loaded in a camera. Used by the switch-roll sheet. */
    val loadedRolls: List<RollWithDetails> = emptyList(),
    /** The roll currently selected for logging. Null until loaded. */
    val activeRoll: RollWithDetails? = null,
    /**
     * Frame number (1-based) currently pointed at by the frame pointer stepper.
     * Persisted per-roll in SharedPreferences.
     */
    val currentFrameNumber: Int = 1,
    /** The actual Frame entity at currentFrameNumber, or null if out of range. */
    val currentFrame: Frame? = null,
    /**
     * The "frontier" frame number — first unlogged frame after the highest logged frame,
     * or frame 1 when nothing has been logged yet. Null when the roll is complete.
     * Used by the UI to show an off-frontier indicator when the user has manually
     * navigated away from the expected next frame.
     */
    val frontierFrameNumber: Int? = null,

    // ---------------------------------------------------------------------------
    // Editable draft fields — pre-populated from the most recently logged frame
    // ---------------------------------------------------------------------------

    val selectedLensId: Int? = null,
    val activeFilterIds: Set<Int> = emptySet(),
    /**
     * Up to 4 most-recently-used filter IDs for the chip row.
     * Sourced from SharedPreferences, filtered to the roll's available filters.
     */
    val mruFilterIds: List<Int> = emptyList(),
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val exposureCompensation: Float? = null,
    val note: String = "",

    // ---------------------------------------------------------------------------
    // Stepper bounds — derived from the active roll's camera body and selected lens
    // ---------------------------------------------------------------------------

    val availableShutterSpeeds: List<String> = emptyList(),
    val availableApertures: List<String> = emptyList(),

    // ---------------------------------------------------------------------------
    // Sheet / dialog state
    // ---------------------------------------------------------------------------

    val showSwitchRollSheet: Boolean = false,
    val isLoading: Boolean = true,
    val isLoggingFrame: Boolean = false,
)

// ---------------------------------------------------------------------------
// Events (one-shot, not part of steady-state UiState)
// ---------------------------------------------------------------------------

sealed class QuickScreenEvent {
    /** Frame logged successfully. UI should trigger confirmation haptic. */
    data class FrameLogged(val frameNumber: Int) : QuickScreenEvent()
    /**
     * User tapped Log Frame but the current frame is already logged.
     * UI should show a confirmation dialog before calling [QuickScreenViewModel.onOverwriteConfirmed].
     */
    data class ConfirmOverwrite(val frameNumber: Int) : QuickScreenEvent()
    /** Frame pointer advanced past the last frame — roll is full. */
    data object RollComplete : QuickScreenEvent()
    data class ShowErrorMessage(val message: String) : QuickScreenEvent()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class QuickScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val rollRepository = RollRepository(db)
    private val frameRepository = FrameRepository(db)
    private val appPreferences = AppPreferences(application)

    private val _state = MutableStateFlow(QuickScreenUiState())
    val state: StateFlow<QuickScreenUiState> = _state.asStateFlow()

    private val _events = Channel<QuickScreenEvent>(Channel.BUFFERED)
    val events: Flow<QuickScreenEvent> = _events.receiveAsFlow()

    // Pending frame + filters for the overwrite-confirmation path
    private var pendingFrame: Frame? = null
    private var pendingFiltersToAdd: List<FrameFilter> = emptyList()
    private var pendingFilterIdsToRemove: List<Int> = emptyList()

    // ---------------------------------------------------------------------------
    // Shutter sound
    // ---------------------------------------------------------------------------

    // SoundPool for the low-latency shutter click. Loaded once in init, released in onCleared.
    // SoundPool is preferred over MediaPlayer for short UI sounds: lower latency, no seek overhead.
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var shutterSoundId: Int = 0
    // True once OnLoadCompleteListener confirms the sample is buffered and ready.
    private var shutterSoundReady: Boolean = false

    private val audioManager = application.getSystemService(AudioManager::class.java)

    init {
        collectLoadedRolls()
        // Register the listener BEFORE load() so the callback is never missed.
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) shutterSoundReady = true
        }
        shutterSoundId = soundPool.load(application, R.raw.shutter_click, 1)
    }

    override fun onCleared() {
        super.onCleared()
        soundPool.release()
    }

    /**
     * Plays the shutter click sound if:
     *   1. The user has not disabled it in Settings → Shooting Defaults.
     *   2. The device ringer is not silenced or in vibrate-only mode.
     *
     * Called after the frame write commits to the database — never on button tap-down.
     */
    private fun playShutterSound() {
        if (!appPreferences.shutterSoundEnabled) return
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (!shutterSoundReady) return  // load() hasn't completed yet
        soundPool.play(shutterSoundId, 1f, 1f, 0, 0, 1f)
    }

    private fun collectLoadedRolls() = viewModelScope.launch {
        rollRepository.getActiveRolls().collect { loadedRolls ->
            val activeRollId = appPreferences.activeRollId
            // Select the active roll, falling back to the most recently loaded if the preference is stale
            val activeRoll = loadedRolls.firstOrNull { it.roll.id == activeRollId }
                ?: loadedRolls.firstOrNull()

            // If the active roll preference points to a roll that no longer exists or isn't loaded,
            // auto-select the first available roll and persist the correction.
            if (activeRoll != null && activeRoll.roll.id != activeRollId) {
                appPreferences.activeRollId = activeRoll.roll.id
            }

            _state.update {
                it.copy(
                    loadedRolls = loadedRolls,
                    activeRoll = activeRoll,
                    isLoading = false,
                )
            }

            // Populate the draft whenever the active roll changes
            if (activeRoll != null) {
                populateDraftForRoll(activeRoll)
            }
        }
    }

    /**
     * Returns the frontier frame number for [frames]: the first unlogged frame after the
     * highest logged frame, or frame 1 when nothing is logged. Returns null when the roll
     * is complete (no unlogged frames remain after the highest logged frame).
     */
    private fun computeFrontierFrameNumber(frames: List<Frame>): Int? {
        val highestLoggedFrameNumber = frames.filter { it.isLogged }.maxOfOrNull { it.frameNumber }
        return if (highestLoggedFrameNumber != null) {
            frames.filter { it.frameNumber > highestLoggedFrameNumber && !it.isLogged }
                .minByOrNull { it.frameNumber }?.frameNumber
        } else {
            frames.minByOrNull { it.frameNumber }?.frameNumber
        }
    }

    /**
     * Pre-populates the draft fields from the most recently logged frame.
     * Called on initial load and when the active roll changes.
     */
    private suspend fun populateDraftForRoll(roll: RollWithDetails) {
        val rollId = roll.roll.id

        // Load the camera body for shutter speed increments
        val body = db.cameraBodyDao().getCameraBodyById(roll.roll.cameraBodyId).first()
        val shutterSpeeds = ExposureValues.shutterSpeeds(body.shutterIncrements)

        // Determine the current frame number from SharedPreferences
        val currentFrameNumber = appPreferences.getCurrentFrameNumber(rollId)
            .coerceIn(1, roll.frames.size.takeIf { it > 0 } ?: 1)
        val currentFrame = roll.frames.firstOrNull { it.frameNumber == currentFrameNumber }

        // Find the most recently logged frame before the current pointer to pre-populate from
        val lastLoggedFrame = roll.frames
            .filter { it.isLogged && it.frameNumber < currentFrameNumber }
            .maxByOrNull { it.frameNumber }

        // Determine primary lens for the draft
        val primaryLensId = roll.lenses.firstOrNull { it.rollLens.isPrimary }?.lens?.id
        val preDraftLensId = lastLoggedFrame?.lensId ?: primaryLensId

        // Pre-populate apertures for the pre-selected lens
        val preDraftLens = roll.lenses.firstOrNull { it.lens.id == preDraftLensId }?.lens
        val apertures = preDraftLens?.let {
            ExposureValues.apertures(it.maxAperture, it.minAperture, it.apertureIncrements)
        } ?: emptyList()

        // Pre-populate filters from the last logged frame's active filters
        val lastFrameFilters = if (lastLoggedFrame != null) {
            frameRepository.getFrameById(lastLoggedFrame.id).first().filters
                .map { it.id }.toSet()
        } else {
            emptySet()
        }

        // MRU filter IDs from SharedPreferences (filtered to this roll's available filters)
        val rollFilterIds = roll.filters.map { it.filter.id }.toSet()
        val mruIds = appPreferences.getMruFilterIds(rollId).filter { it in rollFilterIds }

        _state.update {
            it.copy(
                currentFrameNumber = currentFrameNumber,
                currentFrame = currentFrame,
                selectedLensId = preDraftLensId,
                activeFilterIds = lastFrameFilters.filter { id -> id in rollFilterIds }.toSet(),
                mruFilterIds = mruIds,
                // When no previous frame exists (first frame on roll), default to common field settings.
                aperture = lastLoggedFrame?.aperture ?: apertures.firstOrNull { it == "f/5.6" },
                shutterSpeed = lastLoggedFrame?.shutterSpeed ?: shutterSpeeds.firstOrNull { it == "1/125" },
                exposureCompensation = lastLoggedFrame?.exposureCompensation,
                note = "", // Note does not carry forward between frames
                availableShutterSpeeds = shutterSpeeds,
                availableApertures = apertures,
                frontierFrameNumber = computeFrontierFrameNumber(roll.frames),
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Switch roll sheet
    // ---------------------------------------------------------------------------

    fun onHeaderTapped() {
        if (_state.value.loadedRolls.size > 1) {
            _state.update { it.copy(showSwitchRollSheet = true) }
        }
    }

    fun onSwitchRollSheetDismissed() {
        _state.update { it.copy(showSwitchRollSheet = false) }
    }

    fun onRollSwitched(rollId: Int) = viewModelScope.launch {
        appPreferences.activeRollId = rollId
        val roll = _state.value.loadedRolls.firstOrNull { it.roll.id == rollId } ?: return@launch
        _state.update {
            it.copy(
                activeRoll = roll,
                showSwitchRollSheet = false,
                isLoading = true,
            )
        }
        populateDraftForRoll(roll)
        _state.update { it.copy(isLoading = false) }
        // Sync widget to the newly selected roll
        launch { FrameLogWidgetUpdater.update(getApplication()) }
    }

    // ---------------------------------------------------------------------------
    // Draft field updates
    // ---------------------------------------------------------------------------

    /** Cycles to the next lens in the roll's lens list. */
    fun onLensCycleTapped() {
        val roll = _state.value.activeRoll ?: return
        val lenses = roll.lenses.map { it.lens }
        if (lenses.size <= 1) return
        val currentIdx = lenses.indexOfFirst { it.id == _state.value.selectedLensId }
        val nextLens = lenses[(currentIdx + 1) % lenses.size]
        onLensSelected(nextLens.id)
    }

    fun onLensSelected(lensId: Int) {
        val roll = _state.value.activeRoll ?: return
        val lens = roll.lenses.firstOrNull { it.lens.id == lensId }?.lens ?: return
        val apertures = ExposureValues.apertures(lens.maxAperture, lens.minAperture, lens.apertureIncrements)
        val currentAperture = _state.value.aperture
        val adjustedAperture = if (currentAperture in apertures) currentAperture else apertures.firstOrNull()
        _state.update {
            it.copy(
                selectedLensId = lensId,
                availableApertures = apertures,
                aperture = adjustedAperture,
            )
        }
    }

    fun onFilterToggled(filterId: Int) {
        val rollId = _state.value.activeRoll?.roll?.id ?: return
        val current = _state.value.activeFilterIds.toMutableSet()
        if (filterId in current) current.remove(filterId) else current.add(filterId)

        appPreferences.promoteFilterInMru(rollId, filterId)
        val rollFilterIds = _state.value.activeRoll?.filters?.map { it.filter.id }?.toSet() ?: emptySet()
        val updatedMru = appPreferences.getMruFilterIds(rollId).filter { it in rollFilterIds }

        _state.update { it.copy(activeFilterIds = current, mruFilterIds = updatedMru) }
    }

    fun onApertureChanged(aperture: String) = _state.update { it.copy(aperture = aperture) }

    fun onShutterSpeedChanged(shutterSpeed: String) = _state.update { it.copy(shutterSpeed = shutterSpeed) }

    fun onExposureCompensationChanged(ec: Float?) = _state.update { it.copy(exposureCompensation = ec) }

    fun onNoteChanged(note: String) = _state.update { it.copy(note = note) }

    /**
     * Moves the frame pointer to [frameNumber]. Updates SharedPreferences and re-populates the draft.
     * The UI stepper calls this on each tap.
     */
    fun onFramePointerChanged(frameNumber: Int) = viewModelScope.launch {
        val roll = _state.value.activeRoll ?: return@launch
        val clamped = frameNumber.coerceIn(1, roll.frames.size)
        appPreferences.setCurrentFrameNumber(roll.roll.id, clamped)

        val currentFrame = roll.frames.firstOrNull { it.frameNumber == clamped }

        // If advancing forward past the last frame, emit RollComplete
        if (frameNumber > roll.frames.size) {
            _events.send(QuickScreenEvent.RollComplete)
            return@launch
        }

        _state.update {
            it.copy(
                currentFrameNumber = clamped,
                currentFrame = currentFrame,
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Log Frame
    // ---------------------------------------------------------------------------

    /**
     * Primary action. Validates and either logs the frame immediately, or emits
     * [QuickScreenEvent.ConfirmOverwrite] if the frame was already logged.
     *
     * [lat] and [lng] are provided by the UI if the roll has GPS enabled —
     * the UI is responsible for requesting location permission and obtaining the fix.
     */
    fun onLogFrameTapped(lat: Double? = null, lng: Double? = null) = viewModelScope.launch {
        val s = _state.value
        val currentFrame = s.currentFrame ?: return@launch
        val roll = s.activeRoll ?: return@launch

        // Build the frame write
        val updatedFrame = Frame(
            id = currentFrame.id,
            rollId = currentFrame.rollId,
            frameNumber = currentFrame.frameNumber,
            isLogged = true,
            loggedAt = System.currentTimeMillis(),
            aperture = s.aperture,
            shutterSpeed = s.shutterSpeed,
            lensId = s.selectedLensId,
            exposureCompensation = s.exposureCompensation,
            lat = if (roll.roll.gpsEnabled) lat else null,
            lng = if (roll.roll.gpsEnabled) lng else null,
            notes = s.note.trim().ifBlank { null },
        )

        val previousFilterIds = frameRepository.getFrameById(currentFrame.id).first()
            .filters.map { it.id }.toSet()
        val filtersToAdd = (s.activeFilterIds - previousFilterIds).map { filterId ->
            FrameFilter(frameId = currentFrame.id, filterId = filterId)
        }
        val filterIdsToRemove = (previousFilterIds - s.activeFilterIds).toList()

        if (currentFrame.isLogged) {
            // Overwrite protection — store the pending write and ask for confirmation
            pendingFrame = updatedFrame
            pendingFiltersToAdd = filtersToAdd
            pendingFilterIdsToRemove = filterIdsToRemove
            _events.send(QuickScreenEvent.ConfirmOverwrite(currentFrame.frameNumber))
        } else {
            writeFrame(updatedFrame, filtersToAdd, filterIdsToRemove)
        }
    }

    /** Called by the UI after the user confirms overwriting an already-logged frame. */
    fun onOverwriteConfirmed() = viewModelScope.launch {
        val frame = pendingFrame ?: return@launch
        writeFrame(frame, pendingFiltersToAdd, pendingFilterIdsToRemove)
        pendingFrame = null
        pendingFiltersToAdd = emptyList()
        pendingFilterIdsToRemove = emptyList()
    }

    private suspend fun writeFrame(
        frame: Frame,
        filtersToAdd: List<FrameFilter>,
        filterIdsToRemove: List<Int>,
    ) {
        _state.update { it.copy(isLoggingFrame = true) }
        try {
            frameRepository.logFrame(frame, filtersToAdd, filterIdsToRemove)

            val roll = _state.value.activeRoll ?: return
            val loggedFrameNumber = frame.frameNumber

            // Advance frame pointer to next unlogged frame after the one just logged
            val nextFrame = roll.frames
                .filter { it.frameNumber > loggedFrameNumber && !it.isLogged }
                .minByOrNull { it.frameNumber }

            if (nextFrame != null) {
                appPreferences.setCurrentFrameNumber(roll.roll.id, nextFrame.frameNumber)
                _state.update {
                    it.copy(
                        currentFrameNumber = nextFrame.frameNumber,
                        currentFrame = nextFrame,
                        // nextFrame IS the new frontier — optimistic update avoids a flash of the
                        // off-frontier indicator before the Room Flow re-emits the updated roll.
                        frontierFrameNumber = nextFrame.frameNumber,
                        note = "", // clear the note field for the next frame
                    )
                }
            }

            // Play the shutter click after the DB write — not on tap-down.
            playShutterSound()
            _events.send(QuickScreenEvent.FrameLogged(loggedFrameNumber))
            // Sync widget to the newly logged frame and advanced pointer
            FrameLogWidgetUpdater.update(getApplication())
        } catch (e: Exception) {
            _events.send(QuickScreenEvent.ShowErrorMessage("Failed to log frame: ${e.message}"))
        } finally {
            _state.update { it.copy(isLoggingFrame = false) }
        }
    }
}

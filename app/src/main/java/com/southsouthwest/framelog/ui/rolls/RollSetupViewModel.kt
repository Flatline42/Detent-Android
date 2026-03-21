package com.southsouthwest.framelog.ui.rolls

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.CameraBodyFormat
import com.southsouthwest.framelog.data.db.entity.FilmStock
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.data.db.entity.RollFilter
import com.southsouthwest.framelog.data.db.entity.RollLens
import com.southsouthwest.framelog.data.db.entity.RollStatus
import com.southsouthwest.framelog.data.db.relation.KitWithDetails
import com.southsouthwest.framelog.data.repository.GearRepository
import com.southsouthwest.framelog.data.repository.KitRepository
import com.southsouthwest.framelog.data.repository.RollRepository
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// A lens row in the roll setup lens picker
// ---------------------------------------------------------------------------

data class RollLensRow(
    val lens: Lens,
    val isPrimary: Boolean,
)

// ---------------------------------------------------------------------------
// UiState
// ---------------------------------------------------------------------------

data class RollSetupUiState(
    // Film stock section
    val selectedFilmStock: FilmStock? = null,
    val frameCount: Int = 36,
    /**
     * Push/pull stops. Range -3 to +3, default 0.
     * Null when the user has set a custom ratedISO override.
     */
    val pushPull: Int? = 0,
    /**
     * Effective shooting ISO. Derived from filmStock.iso × 2^pushPull when pushPull is non-null.
     * Manually editable string when pushPull is null (custom override mode).
     */
    val customRatedIsoText: String = "",
    val filmExpiryDate: Long? = null,
    // Camera & kit section
    val selectedCameraBody: CameraBody? = null,
    val selectedLenses: List<RollLensRow> = emptyList(),
    val selectedFilters: List<Filter> = emptyList(),
    // Details
    val gpsEnabled: Boolean = false,
    val rollName: String = "",
    val isRollNameCustomized: Boolean = false,
    // Picker lists
    val availableFilmStocks: List<FilmStock> = emptyList(),
    val availableBodies: List<CameraBody> = emptyList(),
    val availableLenses: List<Lens> = emptyList(), // filtered by camera body mount type
    val availableFilters: List<Filter> = emptyList(),
    // Computed display values (updated reactively as inputs change)
    val ratedISO: Int = 0,
    val totalExposures: Int = 0,
    // Form state
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val filmStockError: String? = null,
    val cameraBodyError: String? = null,
    val lensesError: String? = null,
)

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

sealed class RollSetupEvent {
    /** Roll created (unloaded). Navigate to Roll Journal. */
    data class RollCreated(val rollId: Int) : RollSetupEvent()
    /** Roll created and loaded. Navigate to Quick Screen. */
    data class RollCreatedAndLoaded(val rollId: Int) : RollSetupEvent()
    /** Navigate to Kit Selector screen; returns [KitWithDetails] on success. */
    data object NavigateToKitSelector : RollSetupEvent()
    data class ShowErrorMessage(val message: String) : RollSetupEvent()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class RollSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val gearRepository = GearRepository(db)
    private val kitRepository = KitRepository(db)
    private val rollRepository = RollRepository(db)
    private val appPreferences = AppPreferences(application)

    private val _state = MutableStateFlow(RollSetupUiState())
    val state: StateFlow<RollSetupUiState> = _state.asStateFlow()

    private val _events = Channel<RollSetupEvent>(Channel.BUFFERED)
    val events: Flow<RollSetupEvent> = _events.receiveAsFlow()

    init {
        loadPickerData()
    }

    /**
     * Loads a kit by ID and pre-populates the form. Called from [RollSetupScreen] when the
     * kit selection result is delivered via the navigation back stack SavedStateHandle.
     *
     * Note: kit result passing uses NavBackStackEntry.savedStateHandle (the navigation results
     * handle), which is a different object from the ViewModel's own SavedStateHandle. Observation
     * must therefore happen in the composable — not in the ViewModel init.
     */
    fun loadAndApplyKit(kitId: Int) = viewModelScope.launch {
        val kit = kitRepository.getKitWithDetails(kitId).first()
        onKitSelected(kit)
    }

    private fun loadPickerData() = viewModelScope.launch {
        // Load film stocks and bodies in parallel
        launch {
            gearRepository.searchFilmStocks("").collect { stocks ->
                _state.update { it.copy(availableFilmStocks = stocks, isLoading = false) }
            }
        }
        launch {
            gearRepository.searchCameraBodies("").collect { bodies ->
                _state.update { it.copy(availableBodies = bodies) }
            }
        }
        launch {
            gearRepository.searchFilters("").collect { filters ->
                _state.update { it.copy(availableFilters = filters) }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Film stock section
    // ---------------------------------------------------------------------------

    fun onFilmStockSelected(stock: FilmStock) {
        val s = _state.value
        val newPushPull = s.pushPull ?: 0
        val newRatedISO = computeRatedISO(stock.iso, newPushPull)
        val newFrameCount = stock.defaultFrameCount
        val newTotalExposures = computeTotalExposures(
            frameCount = newFrameCount,
            extraFrames = appPreferences.extraFramesPerRoll,
            isHalfFrame = s.selectedCameraBody?.format == CameraBodyFormat.HALF_FRAME,
        )

        _state.update {
            it.copy(
                selectedFilmStock = stock,
                frameCount = newFrameCount,
                pushPull = newPushPull,
                ratedISO = newRatedISO,
                totalExposures = newTotalExposures,
                rollName = if (!it.isRollNameCustomized) generateRollName(stock) else it.rollName,
                filmStockError = null,
            )
        }
    }

    fun onFrameCountChanged(count: Int) {
        val s = _state.value
        _state.update {
            it.copy(
                frameCount = count,
                totalExposures = computeTotalExposures(
                    frameCount = count,
                    extraFrames = appPreferences.extraFramesPerRoll,
                    isHalfFrame = s.selectedCameraBody?.format == CameraBodyFormat.HALF_FRAME,
                ),
            )
        }
    }

    /**
     * Updates the push/pull actuator. Switches out of custom ISO mode.
     * Recomputes ratedISO from filmStock.iso × 2^pushPull.
     */
    fun onPushPullChanged(stops: Int) {
        val s = _state.value
        val boxISO = s.selectedFilmStock?.iso ?: 0
        val newRatedISO = computeRatedISO(boxISO, stops)
        _state.update {
            it.copy(
                pushPull = stops,
                ratedISO = newRatedISO,
                customRatedIsoText = "",
            )
        }
    }

    /**
     * User typed a custom ISO value, bypassing the push/pull actuator.
     * Sets pushPull = null to indicate custom override mode.
     */
    fun onCustomRatedIsoChanged(text: String) {
        val parsedISO = text.toIntOrNull()
        _state.update {
            it.copy(
                pushPull = null,
                customRatedIsoText = text,
                ratedISO = parsedISO ?: it.ratedISO,
            )
        }
    }

    fun onFilmExpiryDateChanged(epochMillis: Long?) {
        _state.update { it.copy(filmExpiryDate = epochMillis) }
    }

    // ---------------------------------------------------------------------------
    // Camera body / lenses / filters
    // ---------------------------------------------------------------------------

    fun onCameraBodySelected(body: CameraBody) = viewModelScope.launch {
        val s = _state.value
        val compatibleLenses = gearRepository.getLensesByMountType(body.mountType).first()

        // Remove selected lenses that are incompatible with the new mount type
        val compatibleIds = compatibleLenses.map { it.id }.toSet()
        val remainingLenses = s.selectedLenses.filter { it.lens.id in compatibleIds }
        val hasPrimary = remainingLenses.any { it.isPrimary }
        val adjustedLenses = if (!hasPrimary && remainingLenses.isNotEmpty()) {
            remainingLenses.toMutableList().also { it[0] = it[0].copy(isPrimary = true) }
        } else {
            remainingLenses
        }

        val newTotalExposures = computeTotalExposures(
            frameCount = s.frameCount,
            extraFrames = appPreferences.extraFramesPerRoll,
            isHalfFrame = body.format == CameraBodyFormat.HALF_FRAME,
        )

        _state.update {
            it.copy(
                selectedCameraBody = body,
                selectedLenses = adjustedLenses,
                availableLenses = compatibleLenses,
                totalExposures = newTotalExposures,
                cameraBodyError = null,
            )
        }
    }

    fun onLensAdded(lens: Lens) {
        val current = _state.value.selectedLenses
        if (current.any { it.lens.id == lens.id }) return
        val isFirst = current.isEmpty()
        _state.update {
            it.copy(
                selectedLenses = current + RollLensRow(lens = lens, isPrimary = isFirst),
                lensesError = null,
            )
        }
    }

    fun onLensRemoved(lensId: Int) {
        val current = _state.value.selectedLenses.toMutableList()
        val wasRemovingPrimary = current.find { it.lens.id == lensId }?.isPrimary == true
        current.removeAll { it.lens.id == lensId }
        val adjusted = if (wasRemovingPrimary && current.isNotEmpty()) {
            current.also { it[0] = it[0].copy(isPrimary = true) }
        } else {
            current
        }
        _state.update { it.copy(selectedLenses = adjusted) }
    }

    fun onPrimaryLensChanged(lensId: Int) {
        _state.update {
            it.copy(
                selectedLenses = it.selectedLenses.map { row ->
                    row.copy(isPrimary = row.lens.id == lensId)
                }
            )
        }
    }

    fun onFilterToggled(filter: Filter) {
        val current = _state.value.selectedFilters
        val updated = if (current.any { it.id == filter.id }) {
            current.filterNot { it.id == filter.id }
        } else {
            current + filter
        }
        _state.update { it.copy(selectedFilters = updated) }
    }

    // ---------------------------------------------------------------------------
    // Kit pre-population
    // ---------------------------------------------------------------------------

    /**
     * Pre-populates the form from a selected kit. All fields remain editable after this.
     * Called when the user returns from the Kit Selector screen with a selection.
     */
    fun onKitSelected(kit: KitWithDetails) = viewModelScope.launch {
        val body = kit.cameraBody
        val compatibleLenses = gearRepository.getLensesByMountType(body.mountType).first()
        val compatibleIds = compatibleLenses.map { it.id }.toSet()

        val lensRows = kit.lenses
            .filter { it.lens.id in compatibleIds }
            .map { kl -> RollLensRow(lens = kl.lens, isPrimary = kl.kitLens.isPrimary) }
        val filters = kit.filters.map { it.filter }

        val s = _state.value
        val newTotalExposures = computeTotalExposures(
            frameCount = s.frameCount,
            extraFrames = appPreferences.extraFramesPerRoll,
            isHalfFrame = body.format == CameraBodyFormat.HALF_FRAME,
        )

        _state.update {
            it.copy(
                selectedCameraBody = body,
                selectedLenses = lensRows,
                selectedFilters = filters,
                availableLenses = compatibleLenses,
                totalExposures = newTotalExposures,
                cameraBodyError = null,
                lensesError = null,
            )
        }
    }

    fun onLoadKitTapped() = viewModelScope.launch {
        _events.send(RollSetupEvent.NavigateToKitSelector)
    }

    // ---------------------------------------------------------------------------
    // Details
    // ---------------------------------------------------------------------------

    fun onGpsEnabledChanged(enabled: Boolean) {
        _state.update { it.copy(gpsEnabled = enabled) }
    }

    fun onRollNameChanged(name: String) {
        _state.update { it.copy(rollName = name, isRollNameCustomized = name.isNotBlank()) }
    }

    // ---------------------------------------------------------------------------
    // Create actions
    // ---------------------------------------------------------------------------

    /** Creates the roll as unloaded inventory (isLoaded = false). */
    fun onCreateRollTapped() = viewModelScope.launch {
        createRoll(loadAfterCreate = false)
    }

    /** Creates the roll and immediately loads it into a camera (isLoaded = true). */
    fun onCreateAndLoadRollTapped() = viewModelScope.launch {
        createRoll(loadAfterCreate = true)
    }

    private suspend fun createRoll(loadAfterCreate: Boolean) {
        val s = _state.value
        if (!validate(s)) return

        _state.update { it.copy(isSaving = true) }

        val now = System.currentTimeMillis()
        val roll = Roll(
            id = 0,
            name = s.rollName.trim(),
            filmStockId = s.selectedFilmStock!!.id,
            cameraBodyId = s.selectedCameraBody!!.id,
            pushPull = s.pushPull,
            ratedISO = s.ratedISO,
            filmExpiryDate = s.filmExpiryDate,
            totalExposures = s.totalExposures,
            isLoaded = loadAfterCreate,
            gpsEnabled = s.gpsEnabled,
            status = RollStatus.ACTIVE,
            loadedAt = now,
        )

        val rollLenses = s.selectedLenses.map { row ->
            RollLens(rollId = 0, lensId = row.lens.id, isPrimary = row.isPrimary)
        }
        val rollFilters = s.selectedFilters.map { filter ->
            RollFilter(rollId = 0, filterId = filter.id)
        }
        val frames = (1..s.totalExposures).map { frameNumber ->
            Frame(id = 0, rollId = 0, frameNumber = frameNumber)
        }

        val newRollId = rollRepository.createRollWithAssociations(roll, rollLenses, rollFilters, frames)

        // If loaded and no active roll is set, make this the active roll
        if (loadAfterCreate && appPreferences.activeRollId == -1) {
            appPreferences.activeRollId = newRollId.toInt()
        }
        if (loadAfterCreate) {
            FrameLogWidgetUpdater.update(getApplication())
        }

        // If this kit was used, update its lastUsedAt
        // (kit selection not tracked in this ViewModel — handled by KitDetailViewModel)

        _state.update { it.copy(isSaving = false) }

        val event = if (loadAfterCreate) {
            RollSetupEvent.RollCreatedAndLoaded(newRollId.toInt())
        } else {
            RollSetupEvent.RollCreated(newRollId.toInt())
        }
        _events.send(event)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun computeRatedISO(boxISO: Int, pushPull: Int): Int =
        (boxISO * 2.0.pow(pushPull.toDouble())).roundToInt()

    private fun computeTotalExposures(
        frameCount: Int,
        extraFrames: Int,
        isHalfFrame: Boolean,
    ): Int {
        val base = frameCount + extraFrames
        return if (isHalfFrame) base * 2 else base
    }

    private fun generateRollName(stock: FilmStock): String {
        val date = SimpleDateFormat("MMM ''yy", Locale.US).format(Date())
        return "${stock.make} ${stock.name} — $date"
    }

    private fun validate(s: RollSetupUiState): Boolean {
        var valid = true
        if (s.selectedFilmStock == null) {
            _state.update { it.copy(filmStockError = "Select a film stock") }
            valid = false
        }
        if (s.selectedCameraBody == null) {
            _state.update { it.copy(cameraBodyError = "Select a camera body") }
            valid = false
        }
        if (s.selectedLenses.isEmpty()) {
            _state.update { it.copy(lensesError = "Add at least one lens") }
            valid = false
        }
        return valid
    }
}

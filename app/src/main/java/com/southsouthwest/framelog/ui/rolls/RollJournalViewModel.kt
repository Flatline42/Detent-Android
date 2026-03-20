package com.southsouthwest.framelog.ui.rolls

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.ExportFormat
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.relation.RollWithDetails
import com.southsouthwest.framelog.data.repository.RollRepository
import com.southsouthwest.framelog.ui.util.ExportFormatter
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

data class RollJournalUiState(
    val roll: RollWithDetails? = null,
    /** The "current" frame number — the frame the Quick Screen is pointing at. */
    val currentFrameNumber: Int = 1,
    val showExportSheet: Boolean = false,
    val selectedExportFormat: ExportFormat = ExportFormat.CSV,
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
)

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

sealed class RollJournalEvent {
    data class NavigateToFrameDetail(val frameId: Int, val rollId: Int) : RollJournalEvent()
    /** Emit to show the Finish Roll confirmation sheet. */
    data object ShowFinishRollConfirmation : RollJournalEvent()
    /** Emit to show the Load Roll confirmation sheet. */
    data object ShowLoadRollConfirmation : RollJournalEvent()
    /** Emit to show the Archive confirmation sheet. */
    data object ShowArchiveConfirmation : RollJournalEvent()
    /** Emit to show the Unarchive confirmation sheet. */
    data object ShowUnarchiveConfirmation : RollJournalEvent()
    /** Emit to show the delete danger confirmation. */
    data object ShowDeleteConfirmation : RollJournalEvent()
    /**
     * Emit to hand the formatted export content to the Android share sheet.
     * The UI creates the Intent; ViewModel provides the content and MIME type.
     */
    data class ShareExportContent(
        val content: String,
        val mimeType: String,
        val suggestedFilename: String,
    ) : RollJournalEvent()
    data object NavigateBack : RollJournalEvent()
    data class ShowErrorMessage(val message: String) : RollJournalEvent()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class RollJournalViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val rollId: Int = checkNotNull(savedState["rollId"]) {
        "RollJournalViewModel requires a 'rollId' nav argument"
    }

    private val db = AppDatabase.getInstance(application)
    private val rollRepository = RollRepository(db)
    private val appPreferences = AppPreferences(application)

    private val _state = MutableStateFlow(RollJournalUiState())
    val state: StateFlow<RollJournalUiState> = _state.asStateFlow()

    private val _events = Channel<RollJournalEvent>(Channel.BUFFERED)
    val events: Flow<RollJournalEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            rollRepository.getRollById(rollId).collect { rollWithDetails ->
                val currentFrame = appPreferences.getCurrentFrameNumber(rollId)
                _state.update {
                    it.copy(
                        roll = rollWithDetails,
                        currentFrameNumber = currentFrame,
                        isLoading = false,
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // User actions
    // ---------------------------------------------------------------------------

    fun onFrameCardTapped(frameId: Int) = viewModelScope.launch {
        _events.send(RollJournalEvent.NavigateToFrameDetail(frameId, rollId))
    }

    fun onExportTapped() {
        _state.update {
            it.copy(
                showExportSheet = true,
                selectedExportFormat = appPreferences.defaultExportFormat,
            )
        }
    }

    fun onExportFormatSelected(format: ExportFormat) {
        _state.update { it.copy(selectedExportFormat = format) }
    }

    fun onExportDismissed() {
        _state.update { it.copy(showExportSheet = false) }
    }

    fun onExportConfirmed() = viewModelScope.launch {
        _state.update { it.copy(isActionInProgress = true, showExportSheet = false) }
        try {
            val export = rollRepository.getRollForExport(rollId)
            val rollName = _state.value.roll?.roll?.name ?: "roll_$rollId"
            val format = _state.value.selectedExportFormat

            val (content, mimeType, extension) = when (format) {
                ExportFormat.CSV -> Triple(
                    ExportFormatter.toCsv(export),
                    "text/csv",
                    "csv",
                )
                ExportFormat.JSON -> Triple(
                    ExportFormatter.toJson(export),
                    "application/json",
                    "json",
                )
                ExportFormat.PLAIN_TEXT -> Triple(
                    ExportFormatter.toPlainText(export),
                    "text/plain",
                    "txt",
                )
            }

            val filename = sanitizeFilename(rollName) + ".$extension"
            rollRepository.updateLastExported(rollId, System.currentTimeMillis())

            _events.send(
                RollJournalEvent.ShareExportContent(
                    content = content,
                    mimeType = mimeType,
                    suggestedFilename = filename,
                )
            )
        } catch (e: Exception) {
            _events.send(RollJournalEvent.ShowErrorMessage("Export failed: ${e.message}"))
        } finally {
            _state.update { it.copy(isActionInProgress = false) }
        }
    }

    // ---------------------------------------------------------------------------
    // Roll state transitions (confirmation is handled by the UI before calling these)
    // ---------------------------------------------------------------------------

    fun onLoadRollTapped() = viewModelScope.launch {
        _events.send(RollJournalEvent.ShowLoadRollConfirmation)
    }

    fun onLoadRollConfirmed() = viewModelScope.launch {
        _state.update { it.copy(isActionInProgress = true) }
        rollRepository.updateIsLoaded(rollId, true)
        if (appPreferences.activeRollId == -1) {
            appPreferences.activeRollId = rollId
        }
        _state.update { it.copy(isActionInProgress = false) }
    }

    fun onFinishRollTapped() = viewModelScope.launch {
        _events.send(RollJournalEvent.ShowFinishRollConfirmation)
    }

    fun onFinishRollConfirmed() = viewModelScope.launch {
        _state.update { it.copy(isActionInProgress = true) }
        rollRepository.finishRoll(rollId, System.currentTimeMillis())
        // Unload the roll — a finished roll is no longer in a camera
        rollRepository.updateIsLoaded(rollId, false)
        // If this was the active roll, clear the selection
        if (appPreferences.activeRollId == rollId) {
            appPreferences.activeRollId = -1
        }
        _state.update { it.copy(isActionInProgress = false) }
    }

    fun onArchiveTapped() = viewModelScope.launch {
        _events.send(RollJournalEvent.ShowArchiveConfirmation)
    }

    fun onArchiveConfirmed() = viewModelScope.launch {
        _state.update { it.copy(isActionInProgress = true) }
        rollRepository.archiveRoll(rollId)
        _state.update { it.copy(isActionInProgress = false) }
    }

    fun onUnarchiveTapped() = viewModelScope.launch {
        _events.send(RollJournalEvent.ShowUnarchiveConfirmation)
    }

    fun onUnarchiveConfirmed() = viewModelScope.launch {
        _state.update { it.copy(isActionInProgress = true) }
        rollRepository.unarchiveRoll(rollId)
        _state.update { it.copy(isActionInProgress = false) }
    }

    fun onDeleteTapped() = viewModelScope.launch {
        _events.send(RollJournalEvent.ShowDeleteConfirmation)
    }

    fun onDeleteConfirmed() = viewModelScope.launch {
        val roll = _state.value.roll?.roll ?: return@launch
        appPreferences.clearRollPreferences(rollId)
        if (appPreferences.activeRollId == rollId) {
            appPreferences.activeRollId = -1
        }
        rollRepository.deleteRoll(roll)
        _events.send(RollJournalEvent.NavigateBack)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

package com.southsouthwest.framelog.ui.rolls

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.data.db.entity.RollStatus
import com.southsouthwest.framelog.data.db.relation.RollListRow
import com.southsouthwest.framelog.data.repository.RollRepository
import com.southsouthwest.framelog.ui.widget.FrameLogWidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Tab enum
// ---------------------------------------------------------------------------

enum class RollListTab { ACTIVE, FINISHED, ARCHIVED }

// ---------------------------------------------------------------------------
// UiState
// ---------------------------------------------------------------------------

data class RollListUiState(
    val selectedTab: RollListTab = RollListTab.ACTIVE,
    val searchQuery: String = "",
    val activeRolls: List<RollListRow> = emptyList(),
    val finishedRolls: List<RollListRow> = emptyList(),
    val archivedRolls: List<RollListRow> = emptyList(),
    val isLoading: Boolean = true,
)

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

sealed class RollListEvent {
    data object NavigateToRollSetup : RollListEvent()
    data class NavigateToRollJournal(val rollId: Int) : RollListEvent()
    /** Emit to show the Load Roll confirmation sheet for [roll]. */
    data class ShowLoadConfirmation(val roll: Roll) : RollListEvent()
    /** Emit to show the delete danger confirmation for [roll]. */
    data class ShowDeleteConfirmation(val roll: Roll) : RollListEvent()
    data class ShowErrorMessage(val message: String) : RollListEvent()
    /** One-time tip jar nag prompt — emitted once when finished+archived total reaches 5. */
    data object ShowTipNagPrompt : RollListEvent()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class RollListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val rollRepository = RollRepository(db)
    private val appPreferences = AppPreferences(application)

    private val _state = MutableStateFlow(RollListUiState())
    val state: StateFlow<RollListUiState> = _state.asStateFlow()

    private val _events = Channel<RollListEvent>(Channel.BUFFERED)
    val events: Flow<RollListEvent> = _events.receiveAsFlow()

    private val searchQuery = MutableStateFlow("")

    /** Prevents the tip nag from appearing more than once within a single app session. */
    private var tipNagShownThisSession = false

    init {
        collectActiveRolls()
        collectFinishedRolls()
        collectArchivedRolls()
    }

    private fun collectActiveRolls() = viewModelScope.launch {
        searchQuery.debounce(300).flatMapLatest { query ->
            rollRepository.searchRollListRowsByStatus(query, RollStatus.ACTIVE.value)
        }.collect { rows ->
            _state.update { it.copy(activeRolls = rows, isLoading = false) }
        }
    }

    private fun collectFinishedRolls() = viewModelScope.launch {
        searchQuery.debounce(300).flatMapLatest { query ->
            rollRepository.searchRollListRowsByStatus(query, RollStatus.FINISHED.value)
        }.collect { rows ->
            _state.update { it.copy(finishedRolls = rows) }
            checkTipNag(_state.value)
        }
    }

    private fun collectArchivedRolls() = viewModelScope.launch {
        searchQuery.debounce(300).flatMapLatest { query ->
            rollRepository.searchRollListRowsByStatus(query, RollStatus.ARCHIVED.value)
        }.collect { rows ->
            _state.update { it.copy(archivedRolls = rows) }
            checkTipNag(_state.value)
        }
    }

    /**
     * Emits [RollListEvent.ShowTipNagPrompt] the first time the combined finished+archived
     * roll count reaches 5, subject to the persistent [AppPreferences.tipJarPromptShown] gate
     * and the per-session [tipNagShownThisSession] guard.
     */
    private fun checkTipNag(currentState: RollListUiState) {
        val total = currentState.finishedRolls.size + currentState.archivedRolls.size
        if (total >= 5 && !appPreferences.tipJarPromptShown && !tipNagShownThisSession) {
            tipNagShownThisSession = true
            viewModelScope.launch { _events.send(RollListEvent.ShowTipNagPrompt) }
        }
    }

    // ---------------------------------------------------------------------------
    // User actions
    // ---------------------------------------------------------------------------

    fun onTabSelected(tab: RollListTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        _state.update { it.copy(searchQuery = query) }
    }

    fun onAddRollTapped() = viewModelScope.launch {
        _events.send(RollListEvent.NavigateToRollSetup)
    }

    fun onRollCardTapped(rollId: Int) = viewModelScope.launch {
        _events.send(RollListEvent.NavigateToRollJournal(rollId))
    }

    fun onLoadRollRequested(roll: Roll) = viewModelScope.launch {
        // Show confirmation sheet — actual load happens in onLoadRollConfirmed
        _events.send(RollListEvent.ShowLoadConfirmation(roll))
    }

    fun onLoadRollConfirmed(roll: Roll) = viewModelScope.launch {
        rollRepository.updateIsLoaded(roll.id, true)
        // If no active roll is currently set, make this the active roll for the Quick Screen
        if (appPreferences.activeRollId == -1) {
            appPreferences.activeRollId = roll.id
        }
        launch { FrameLogWidgetUpdater.update(getApplication()) }
    }

    fun onDeleteTapped(roll: Roll) = viewModelScope.launch {
        _events.send(RollListEvent.ShowDeleteConfirmation(roll))
    }

    fun onDeleteConfirmed(roll: Roll) = viewModelScope.launch {
        // Clear SharedPreferences for this roll
        appPreferences.clearRollPreferences(roll.id)
        // If this was the active roll, clear the active roll selection
        if (appPreferences.activeRollId == roll.id) {
            appPreferences.activeRollId = -1
        }
        rollRepository.deleteRoll(roll)
        launch { FrameLogWidgetUpdater.update(getApplication()) }
    }

    /**
     * Archives a finished roll. The UI must show a confirmation dialog before calling this —
     * archiving is a one-step state transition (archive/unarchive) not a destructive action,
     * but we still confirm per spec.
     */
    fun onArchiveConfirmed(roll: Roll) = viewModelScope.launch {
        rollRepository.archiveRoll(roll.id)
    }

    /**
     * Unarchives a roll back to finished status. The UI must confirm before calling this.
     */
    fun onUnarchiveConfirmed(roll: Roll) = viewModelScope.launch {
        rollRepository.unarchiveRoll(roll.id)
    }

    /** User tapped "Sure!" on the tip nag — mark it shown so it never reappears. */
    fun onTipNagAccepted() {
        appPreferences.tipJarPromptShown = true
    }

    /** User tapped "No thanks" on the tip nag — mark it shown so it never reappears. */
    fun onTipNagDeclined() {
        appPreferences.tipJarPromptShown = true
    }
}

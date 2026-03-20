package com.southsouthwest.framelog.ui.rolls

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.data.db.entity.RollStatus
import com.southsouthwest.framelog.data.repository.RollRepository
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
    val activeRolls: List<Roll> = emptyList(),
    val finishedRolls: List<Roll> = emptyList(),
    val archivedRolls: List<Roll> = emptyList(),
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

    init {
        collectActiveRolls()
        collectFinishedRolls()
        collectArchivedRolls()
    }

    private fun collectActiveRolls() = viewModelScope.launch {
        searchQuery.debounce(300).flatMapLatest { query ->
            rollRepository.searchRollsByStatus(query, RollStatus.ACTIVE.value)
        }.collect { rolls ->
            _state.update { it.copy(activeRolls = rolls, isLoading = false) }
        }
    }

    private fun collectFinishedRolls() = viewModelScope.launch {
        searchQuery.debounce(300).flatMapLatest { query ->
            rollRepository.searchRollsByStatus(query, RollStatus.FINISHED.value)
        }.collect { rolls ->
            _state.update { it.copy(finishedRolls = rolls) }
        }
    }

    private fun collectArchivedRolls() = viewModelScope.launch {
        searchQuery.debounce(300).flatMapLatest { query ->
            rollRepository.searchRollsByStatus(query, RollStatus.ARCHIVED.value)
        }.collect { rolls ->
            _state.update { it.copy(archivedRolls = rolls) }
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

    fun onLoadRollSwipedOrLongPressed(roll: Roll) = viewModelScope.launch {
        // Show confirmation sheet — actual load happens in onLoadRollConfirmed
        _events.send(RollListEvent.ShowLoadConfirmation(roll))
    }

    fun onLoadRollConfirmed(roll: Roll) = viewModelScope.launch {
        rollRepository.updateIsLoaded(roll.id, true)
        // If no active roll is currently set, make this the active roll
        if (appPreferences.activeRollId == -1) {
            appPreferences.activeRollId = roll.id
        }
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
    }

    fun onArchiveTapped(roll: Roll) = viewModelScope.launch {
        // Archive is a confirmation action — the UI shows a sheet before calling this
        rollRepository.archiveRoll(roll.id)
    }

    fun onUnarchiveTapped(roll: Roll) = viewModelScope.launch {
        rollRepository.unarchiveRoll(roll.id)
    }
}

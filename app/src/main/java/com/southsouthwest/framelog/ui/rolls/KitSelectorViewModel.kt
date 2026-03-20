package com.southsouthwest.framelog.ui.rolls

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.relation.KitWithDetails
import com.southsouthwest.framelog.data.repository.KitRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KitSelectorUiState(
    val kits: List<KitWithDetails> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
)

sealed class KitSelectorEvent {
    /** User selected a kit — navigate back to Roll Setup with this kit's data pre-filled. */
    data class KitSelected(val kit: KitWithDetails) : KitSelectorEvent()
    /** User tapped FAB to create a new kit; navigate to Kit Detail, return on save. */
    data object NavigateToNewKit : KitSelectorEvent()
}

class KitSelectorViewModel(application: Application) : AndroidViewModel(application) {

    private val kitRepository = KitRepository(AppDatabase.getInstance(application))

    private val _searchQuery = MutableStateFlow("")

    // Kits are loaded as KitWithDetails so the selector card can show the full gear preview
    // (body name, lens names, filter chips) without additional queries.
    // Note: KitDao.searchKits returns Kit, not KitWithDetails. We need KitWithDetails per kit.
    // Since there's no "searchKitsWithDetails" query, we search kits and then resolve details.
    // For simplicity, load all kits with details and filter in the ViewModel.
    private val _allKits = MutableStateFlow<List<KitWithDetails>>(emptyList())

    private val _state = MutableStateFlow(KitSelectorUiState())
    val state: StateFlow<KitSelectorUiState> = _state.asStateFlow()

    private val _events = Channel<KitSelectorEvent>(Channel.BUFFERED)
    val events: Flow<KitSelectorEvent> = _events.receiveAsFlow()

    init {
        // Collect the full kit list (search within ViewModel since KitWithDetails has no DAO search)
        viewModelScope.launch {
            kitRepository.searchKits("").collect { kits ->
                // Resolve each Kit to KitWithDetails using .first() on the Flow so we
                // take only one DB snapshot and don't block indefinitely on the collector.
                val detailed = kits.mapNotNull { kit ->
                    try {
                        kitRepository.getKitWithDetails(kit.id).first()
                    } catch (e: Exception) {
                        null
                    }
                }
                _allKits.value = detailed
                applyFilter()
            }
        }

        viewModelScope.launch {
            _searchQuery.debounce(300).collect {
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val query = _searchQuery.value.trim().lowercase()
        val filtered = if (query.isBlank()) {
            _allKits.value
        } else {
            _allKits.value.filter { kit ->
                kit.kit.name.lowercase().contains(query) ||
                    kit.cameraBody.name.lowercase().contains(query) ||
                    kit.cameraBody.make.lowercase().contains(query)
            }
        }
        _state.update {
            it.copy(kits = filtered, searchQuery = _searchQuery.value, isLoading = false)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _state.update { it.copy(searchQuery = query) }
    }

    fun onKitSelected(kit: KitWithDetails) = viewModelScope.launch {
        _events.send(KitSelectorEvent.KitSelected(kit))
    }

    fun onCreateNewKitTapped() = viewModelScope.launch {
        _events.send(KitSelectorEvent.NavigateToNewKit)
    }
}

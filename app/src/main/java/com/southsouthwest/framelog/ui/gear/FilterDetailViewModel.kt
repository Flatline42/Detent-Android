package com.southsouthwest.framelog.ui.gear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.repository.GearRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilterDetailUiState(
    val id: Int = 0,
    val name: String = "",
    val make: String = "",
    val filterType: String = "",
    /** String representation of the EV reduction; blank = null in DB. */
    val evReduction: String = "",
    /** String representation of the filter size in mm; blank = null in DB. */
    val filterSizeMm: String = "",
    val notes: String = "",
    // Autocomplete — existing filterType values from the database (folksonomy)
    val filterTypeSuggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val nameError: String? = null,
    val makeError: String? = null,
    val filterTypeError: String? = null,
    val evReductionError: String? = null,
)

sealed class FilterDetailEvent {
    data object SaveSuccessful : FilterDetailEvent()
    data object DeleteSuccessful : FilterDetailEvent()
    data object ConfirmDiscard : FilterDetailEvent()
}

class FilterDetailViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val filterId: Int = savedState["id"] ?: 0
    private val gearRepository = GearRepository(AppDatabase.getInstance(application))

    private val _state = MutableStateFlow(FilterDetailUiState(id = filterId, isLoading = filterId != 0))
    val state: StateFlow<FilterDetailUiState> = _state.asStateFlow()

    private val _events = Channel<FilterDetailEvent>(Channel.BUFFERED)
    val events: Flow<FilterDetailEvent> = _events.receiveAsFlow()

    init {
        loadFilterTypeSuggestions()
        if (filterId != 0) loadExistingFilter()
    }

    private fun loadFilterTypeSuggestions() = viewModelScope.launch {
        gearRepository.getDistinctFilterTypes().collect { types ->
            _state.update { it.copy(filterTypeSuggestions = types) }
        }
    }

    private fun loadExistingFilter() = viewModelScope.launch {
        val filter = gearRepository.getFilterById(filterId).first()
        _state.update {
            it.copy(
                name = filter.name,
                make = filter.make,
                filterType = filter.filterType,
                evReduction = filter.evReduction?.toString() ?: "",
                filterSizeMm = filter.filterSizeMm?.toString() ?: "",
                notes = filter.notes ?: "",
                isLoading = false,
                isDirty = false,
            )
        }
    }

    fun onNameChanged(value: String) =
        _state.update { it.copy(name = value, isDirty = true, nameError = null) }

    fun onMakeChanged(value: String) =
        _state.update { it.copy(make = value, isDirty = true, makeError = null) }

    fun onFilterTypeChanged(value: String) =
        _state.update { it.copy(filterType = value, isDirty = true, filterTypeError = null) }

    fun onEvReductionChanged(value: String) =
        _state.update { it.copy(evReduction = value, isDirty = true, evReductionError = null) }

    fun onFilterSizeChanged(value: String) =
        _state.update { it.copy(filterSizeMm = value, isDirty = true) }

    fun onNotesChanged(value: String) =
        _state.update { it.copy(notes = value, isDirty = true) }

    fun onSaveTapped() = viewModelScope.launch {
        val s = _state.value
        if (!validate(s)) return@launch

        _state.update { it.copy(isSaving = true) }

        // Blank evReduction = no meaningful exposure effect (e.g. UV filter)
        val evFloat = s.evReduction.toFloatOrNull()

        val filter = Filter(
            id = filterId,
            name = s.name.trim(),
            make = s.make.trim(),
            filterType = s.filterType.trim(),
            evReduction = evFloat,
            filterSizeMm = s.filterSizeMm.toIntOrNull(),
            notes = s.notes.trim().ifBlank { null },
        )

        if (filterId == 0) gearRepository.insertFilter(filter)
        else gearRepository.updateFilter(filter)

        _state.update { it.copy(isSaving = false, isDirty = false) }
        _events.send(FilterDetailEvent.SaveSuccessful)
    }

    fun onDeleteConfirmed() = viewModelScope.launch {
        if (filterId == 0) return@launch
        val s = _state.value
        val filter = Filter(
            id = filterId,
            name = s.name,
            make = s.make,
            filterType = s.filterType,
            evReduction = s.evReduction.toFloatOrNull(),
            filterSizeMm = s.filterSizeMm.toIntOrNull(),
        )
        gearRepository.deleteFilter(filter)
        _events.send(FilterDetailEvent.DeleteSuccessful)
    }

    fun onBackPressed() = viewModelScope.launch {
        if (_state.value.isDirty) _events.send(FilterDetailEvent.ConfirmDiscard)
    }

    private fun validate(s: FilterDetailUiState): Boolean {
        var valid = true
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            valid = false
        }
        if (s.make.isBlank()) {
            _state.update { it.copy(makeError = "Make is required") }
            valid = false
        }
        if (s.filterType.isBlank()) {
            _state.update { it.copy(filterTypeError = "Filter type is required") }
            valid = false
        }
        // evReduction is optional (blank = null); if provided it must be a positive number
        val ev = s.evReduction
        if (ev.isNotBlank()) {
            val f = ev.toFloatOrNull()
            if (f == null || f <= 0f) {
                _state.update { it.copy(evReductionError = "EV reduction must be a positive number (e.g. 3.0)") }
                valid = false
            }
        }
        return valid
    }
}

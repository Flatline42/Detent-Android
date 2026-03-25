package com.southsouthwest.framelog.ui.gear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.ColorType
import com.southsouthwest.framelog.data.db.entity.FilmFormat
import com.southsouthwest.framelog.data.db.entity.FilmStock
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

data class FilmStockDetailUiState(
    val id: Int = 0,
    val name: String = "",
    val make: String = "",
    val iso: String = "", // String for text field; validated as positive Int on save
    val format: FilmFormat = FilmFormat.THIRTY_FIVE_MM,
    val defaultFrameCount: String = "36",
    val colorType: ColorType = ColorType.COLOR_NEGATIVE,
    val discontinued: Boolean = false,
    val notes: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val nameError: String? = null,
    val makeError: String? = null,
    val isoError: String? = null,
    val defaultFrameCountError: String? = null,
)

sealed class FilmStockDetailEvent {
    data object SaveSuccessful : FilmStockDetailEvent()
    data object DeleteSuccessful : FilmStockDetailEvent()
    data object ConfirmDiscard : FilmStockDetailEvent()
}

class FilmStockDetailViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val filmStockId: Int = savedState["id"] ?: 0
    private val gearRepository = GearRepository(AppDatabase.getInstance(application))

    private val _state = MutableStateFlow(
        FilmStockDetailUiState(id = filmStockId, isLoading = filmStockId != 0)
    )
    val state: StateFlow<FilmStockDetailUiState> = _state.asStateFlow()

    private val _events = Channel<FilmStockDetailEvent>(Channel.BUFFERED)
    val events: Flow<FilmStockDetailEvent> = _events.receiveAsFlow()

    init {
        if (filmStockId != 0) loadExistingFilmStock()
    }

    private fun loadExistingFilmStock() = viewModelScope.launch {
        val stock = gearRepository.getFilmStockById(filmStockId).first()
        _state.update {
            it.copy(
                name = stock.name,
                make = stock.make,
                iso = stock.iso.toString(),
                format = stock.format,
                defaultFrameCount = stock.defaultFrameCount.toString(),
                colorType = stock.colorType,
                discontinued = stock.discontinued,
                notes = stock.notes ?: "",
                isLoading = false,
                isDirty = false,
            )
        }
    }

    fun onNameChanged(value: String) =
        _state.update { it.copy(name = value, isDirty = true, nameError = null) }

    fun onMakeChanged(value: String) =
        _state.update { it.copy(make = value, isDirty = true, makeError = null) }

    fun onIsoChanged(value: String) =
        _state.update { it.copy(iso = value, isDirty = true, isoError = null) }

    fun onFormatChanged(value: FilmFormat) =
        _state.update { it.copy(format = value, isDirty = true) }

    fun onDefaultFrameCountChanged(value: String) =
        _state.update { it.copy(defaultFrameCount = value, isDirty = true, defaultFrameCountError = null) }

    fun onColorTypeChanged(value: ColorType) =
        _state.update { it.copy(colorType = value, isDirty = true) }

    fun onDiscontinuedChanged(value: Boolean) =
        _state.update { it.copy(discontinued = value, isDirty = true) }

    fun onNotesChanged(value: String) =
        _state.update { it.copy(notes = value, isDirty = true) }

    fun onSaveTapped() = viewModelScope.launch {
        val s = _state.value
        if (!validate(s)) return@launch

        _state.update { it.copy(isSaving = true) }

        val stock = FilmStock(
            id = filmStockId,
            name = s.name.trim(),
            make = s.make.trim(),
            iso = s.iso.toInt(),
            format = s.format,
            defaultFrameCount = s.defaultFrameCount.toInt(),
            colorType = s.colorType,
            discontinued = s.discontinued,
            notes = s.notes.trim().ifBlank { null },
        )

        if (filmStockId == 0) gearRepository.insertFilmStock(stock)
        else gearRepository.updateFilmStock(stock)

        _state.update { it.copy(isSaving = false, isDirty = false) }
        _events.send(FilmStockDetailEvent.SaveSuccessful)
    }

    fun onDeleteConfirmed() = viewModelScope.launch {
        if (filmStockId == 0) return@launch
        val s = _state.value
        val stock = FilmStock(
            id = filmStockId,
            name = s.name,
            make = s.make,
            iso = s.iso.toIntOrNull() ?: 0,
            format = s.format,
            defaultFrameCount = s.defaultFrameCount.toIntOrNull() ?: 36,
            colorType = s.colorType,
            discontinued = s.discontinued,
        )
        gearRepository.deleteFilmStock(stock)
        _events.send(FilmStockDetailEvent.DeleteSuccessful)
    }

    fun onBackPressed() = viewModelScope.launch {
        if (_state.value.isDirty) _events.send(FilmStockDetailEvent.ConfirmDiscard)
    }

    private fun validate(s: FilmStockDetailUiState): Boolean {
        var valid = true
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            valid = false
        }
        if (s.make.isBlank()) {
            _state.update { it.copy(makeError = "Make is required") }
            valid = false
        }
        val iso = s.iso.toIntOrNull()
        if (iso == null || iso <= 0) {
            _state.update { it.copy(isoError = "ISO must be a positive number") }
            valid = false
        }
        val frames = s.defaultFrameCount.toIntOrNull()
        if (frames == null || frames <= 0) {
            _state.update { it.copy(defaultFrameCountError = "Frame count must be a positive number") }
            valid = false
        }
        return valid
    }
}

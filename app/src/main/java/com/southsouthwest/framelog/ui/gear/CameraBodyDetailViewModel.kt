package com.southsouthwest.framelog.ui.gear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.CameraBodyFormat
import com.southsouthwest.framelog.data.db.entity.ShutterIncrements
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

data class CameraBodyDetailUiState(
    val id: Int = 0,
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val mountType: String = "",
    val format: CameraBodyFormat = CameraBodyFormat.THIRTY_FIVE_MM,
    val shutterIncrements: ShutterIncrements = ShutterIncrements.FULL,
    val notes: String = "",
    // Mount type autocomplete — sourced from existing Lens mount types
    val mountTypeSuggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val nameError: String? = null,
    val makeError: String? = null,
    val modelError: String? = null,
    val mountTypeError: String? = null,
)

sealed class CameraBodyDetailEvent {
    data object SaveSuccessful : CameraBodyDetailEvent()
    data object DeleteSuccessful : CameraBodyDetailEvent()
    data object ConfirmDiscard : CameraBodyDetailEvent()
}

class CameraBodyDetailViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val bodyId: Int = savedState["id"] ?: 0
    private val gearRepository = GearRepository(AppDatabase.getInstance(application))

    private val _state = MutableStateFlow(CameraBodyDetailUiState(id = bodyId, isLoading = bodyId != 0))
    val state: StateFlow<CameraBodyDetailUiState> = _state.asStateFlow()

    private val _events = Channel<CameraBodyDetailEvent>(Channel.BUFFERED)
    val events: Flow<CameraBodyDetailEvent> = _events.receiveAsFlow()

    init {
        loadMountTypeSuggestions()
        if (bodyId != 0) loadExistingBody()
    }

    private fun loadMountTypeSuggestions() = viewModelScope.launch {
        // Lens is the vocabulary source for mount types; bodies pick from existing lens mounts.
        gearRepository.getDistinctMountTypes().collect { types ->
            _state.update { it.copy(mountTypeSuggestions = types) }
        }
    }

    private fun loadExistingBody() = viewModelScope.launch {
        val body = gearRepository.getCameraBodyById(bodyId).first()
        _state.update {
            it.copy(
                name = body.name,
                make = body.make,
                model = body.model,
                mountType = body.mountType,
                format = body.format,
                shutterIncrements = body.shutterIncrements,
                notes = body.notes ?: "",
                isLoading = false,
                isDirty = false,
            )
        }
    }

    fun onNameChanged(value: String) =
        _state.update { it.copy(name = value, isDirty = true, nameError = null) }

    fun onMakeChanged(value: String) =
        _state.update { it.copy(make = value, isDirty = true, makeError = null) }

    fun onModelChanged(value: String) =
        _state.update { it.copy(model = value, isDirty = true, modelError = null) }

    fun onMountTypeChanged(value: String) =
        _state.update { it.copy(mountType = value, isDirty = true, mountTypeError = null) }

    fun onFormatChanged(value: CameraBodyFormat) =
        _state.update { it.copy(format = value, isDirty = true) }

    fun onShutterIncrementsChanged(value: ShutterIncrements) =
        _state.update { it.copy(shutterIncrements = value, isDirty = true) }

    fun onNotesChanged(value: String) =
        _state.update { it.copy(notes = value, isDirty = true) }

    fun onSaveTapped() = viewModelScope.launch {
        val s = _state.value
        if (!validate(s)) return@launch

        _state.update { it.copy(isSaving = true) }

        val body = CameraBody(
            id = bodyId,
            name = s.name.trim(),
            make = s.make.trim(),
            model = s.model.trim(),
            mountType = s.mountType.trim(),
            format = s.format,
            shutterIncrements = s.shutterIncrements,
            notes = s.notes.trim().ifBlank { null },
        )

        if (bodyId == 0) gearRepository.insertCameraBody(body)
        else gearRepository.updateCameraBody(body)

        _state.update { it.copy(isSaving = false, isDirty = false) }
        _events.send(CameraBodyDetailEvent.SaveSuccessful)
    }

    fun onDeleteConfirmed() = viewModelScope.launch {
        if (bodyId == 0) return@launch
        val s = _state.value
        val body = CameraBody(
            id = bodyId,
            name = s.name,
            make = s.make,
            model = s.model,
            mountType = s.mountType,
            format = s.format,
            shutterIncrements = s.shutterIncrements,
        )
        gearRepository.deleteCameraBody(body)
        _events.send(CameraBodyDetailEvent.DeleteSuccessful)
    }

    fun onBackPressed() = viewModelScope.launch {
        if (_state.value.isDirty) _events.send(CameraBodyDetailEvent.ConfirmDiscard)
    }

    private fun validate(s: CameraBodyDetailUiState): Boolean {
        var valid = true
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            valid = false
        }
        if (s.make.isBlank()) {
            _state.update { it.copy(makeError = "Make is required") }
            valid = false
        }
        if (s.model.isBlank()) {
            _state.update { it.copy(modelError = "Model is required") }
            valid = false
        }
        if (s.mountType.isBlank()) {
            _state.update { it.copy(mountTypeError = "Mount type is required") }
            valid = false
        }
        return valid
    }
}

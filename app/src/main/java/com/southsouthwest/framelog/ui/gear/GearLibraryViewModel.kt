package com.southsouthwest.framelog.ui.gear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.FilmStock
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Kit
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.data.repository.GearRepository
import com.southsouthwest.framelog.data.repository.KitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Tab enum
// ---------------------------------------------------------------------------

enum class GearTab { LENSES, BODIES, FILTERS, FILM_STOCKS, KITS }

// ---------------------------------------------------------------------------
// Sort enums
// ---------------------------------------------------------------------------

enum class LensSort {
    NAME_ASC,
    MOUNT_TYPE,
    RECENTLY_ADDED,
}

enum class BodySort {
    NAME_ASC,
    RECENTLY_ADDED,
}

enum class FilterSort {
    NAME_ASC,
    FILTER_SIZE,
    RECENTLY_ADDED,
}

enum class FilmStockSort {
    NAME_ASC,
    RECENTLY_ADDED,
}

enum class KitSort {
    NAME_ASC,
    LAST_USED,
    RECENTLY_ADDED,
}

// ---------------------------------------------------------------------------
// UiState
// ---------------------------------------------------------------------------

data class GearLibraryUiState(
    val selectedTab: GearTab = GearTab.LENSES,
    // Per-tab search queries (persist across tab switches)
    val lensQuery: String = "",
    val bodyQuery: String = "",
    val filterQuery: String = "",
    val filmStockQuery: String = "",
    val kitQuery: String = "",
    // Sort options per tab
    val lensSort: LensSort = LensSort.NAME_ASC,
    val bodySort: BodySort = BodySort.NAME_ASC,
    val filterSort: FilterSort = FilterSort.NAME_ASC,
    val filmStockSort: FilmStockSort = FilmStockSort.NAME_ASC,
    val kitSort: KitSort = KitSort.NAME_ASC,
    // Results
    val lenses: List<Lens> = emptyList(),
    val bodies: List<CameraBody> = emptyList(),
    val filters: List<Filter> = emptyList(),
    val filmStocks: List<FilmStock> = emptyList(),
    val kits: List<Kit> = emptyList(),
    val isLoading: Boolean = true,
)

// ---------------------------------------------------------------------------
// Events (one-shot navigation/dialog triggers)
// ---------------------------------------------------------------------------

sealed class GearLibraryEvent {
    data object NavigateToNewLens : GearLibraryEvent()
    data class NavigateToLensDetail(val lensId: Int) : GearLibraryEvent()
    data object NavigateToNewBody : GearLibraryEvent()
    data class NavigateToBodyDetail(val bodyId: Int) : GearLibraryEvent()
    data object NavigateToNewFilter : GearLibraryEvent()
    data class NavigateToFilterDetail(val filterId: Int) : GearLibraryEvent()
    data object NavigateToNewFilmStock : GearLibraryEvent()
    data class NavigateToFilmStockDetail(val filmStockId: Int) : GearLibraryEvent()
    data object NavigateToNewKit : GearLibraryEvent()
    data class NavigateToKitDetail(val kitId: Int) : GearLibraryEvent()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class GearLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val gearRepository = GearRepository(db)
    private val kitRepository = KitRepository(db)

    private val _state = MutableStateFlow(GearLibraryUiState())
    val state: StateFlow<GearLibraryUiState> = _state.asStateFlow()

    private val _events = Channel<GearLibraryEvent>(Channel.BUFFERED)
    val events: Flow<GearLibraryEvent> = _events.receiveAsFlow()

    // Debounced query flows — each tab tracks independently
    private val lensQuery = MutableStateFlow("")
    private val bodyQuery = MutableStateFlow("")
    private val filterQuery = MutableStateFlow("")
    private val filmStockQuery = MutableStateFlow("")
    private val kitQuery = MutableStateFlow("")

    private val lensSort = MutableStateFlow(LensSort.NAME_ASC)
    private val bodySort = MutableStateFlow(BodySort.NAME_ASC)
    private val filterSort = MutableStateFlow(FilterSort.NAME_ASC)
    private val filmStockSort = MutableStateFlow(FilmStockSort.NAME_ASC)
    private val kitSort = MutableStateFlow(KitSort.NAME_ASC)

    init {
        collectLenses()
        collectBodies()
        collectFilters()
        collectFilmStocks()
        collectKits()
    }

    // ---------------------------------------------------------------------------
    // Data collection — one coroutine per tab
    // ---------------------------------------------------------------------------

    private fun collectLenses() = viewModelScope.launch {
        combine(
            lensQuery.debounce(300).flatMapLatest { gearRepository.searchLenses(it) },
            lensSort,
        ) { lenses, sort ->
            lenses.sortedWith(sort.comparator)
        }.collect { sorted ->
            _state.update { it.copy(lenses = sorted, isLoading = false) }
        }
    }

    private fun collectBodies() = viewModelScope.launch {
        combine(
            bodyQuery.debounce(300).flatMapLatest { gearRepository.searchCameraBodies(it) },
            bodySort,
        ) { bodies, sort ->
            bodies.sortedWith(sort.comparator)
        }.collect { sorted ->
            _state.update { it.copy(bodies = sorted) }
        }
    }

    private fun collectFilters() = viewModelScope.launch {
        combine(
            filterQuery.debounce(300).flatMapLatest { gearRepository.searchFilters(it) },
            filterSort,
        ) { filters, sort ->
            filters.sortedWith(sort.comparator)
        }.collect { sorted ->
            _state.update { it.copy(filters = sorted) }
        }
    }

    private fun collectFilmStocks() = viewModelScope.launch {
        combine(
            filmStockQuery.debounce(300).flatMapLatest { gearRepository.searchFilmStocks(it) },
            filmStockSort,
        ) { stocks, sort ->
            stocks.sortedWith(sort.comparator)
        }.collect { sorted ->
            _state.update { it.copy(filmStocks = sorted) }
        }
    }

    private fun collectKits() = viewModelScope.launch {
        combine(
            kitQuery.debounce(300).flatMapLatest { kitRepository.searchKits(it) },
            kitSort,
        ) { kits, sort ->
            kits.sortedWith(sort.comparator)
        }.collect { sorted ->
            _state.update { it.copy(kits = sorted) }
        }
    }

    // ---------------------------------------------------------------------------
    // User actions — tab selection
    // ---------------------------------------------------------------------------

    fun onTabSelected(tab: GearTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    // ---------------------------------------------------------------------------
    // User actions — search
    // ---------------------------------------------------------------------------

    fun onLensQueryChanged(query: String) {
        lensQuery.value = query
        _state.update { it.copy(lensQuery = query) }
    }

    fun onBodyQueryChanged(query: String) {
        bodyQuery.value = query
        _state.update { it.copy(bodyQuery = query) }
    }

    fun onFilterQueryChanged(query: String) {
        filterQuery.value = query
        _state.update { it.copy(filterQuery = query) }
    }

    fun onFilmStockQueryChanged(query: String) {
        filmStockQuery.value = query
        _state.update { it.copy(filmStockQuery = query) }
    }

    fun onKitQueryChanged(query: String) {
        kitQuery.value = query
        _state.update { it.copy(kitQuery = query) }
    }

    // ---------------------------------------------------------------------------
    // User actions — sort
    // ---------------------------------------------------------------------------

    fun onLensSortChanged(sort: LensSort) {
        lensSort.value = sort
        _state.update { it.copy(lensSort = sort) }
    }

    fun onBodySortChanged(sort: BodySort) {
        bodySort.value = sort
        _state.update { it.copy(bodySort = sort) }
    }

    fun onFilterSortChanged(sort: FilterSort) {
        filterSort.value = sort
        _state.update { it.copy(filterSort = sort) }
    }

    fun onFilmStockSortChanged(sort: FilmStockSort) {
        filmStockSort.value = sort
        _state.update { it.copy(filmStockSort = sort) }
    }

    fun onKitSortChanged(sort: KitSort) {
        kitSort.value = sort
        _state.update { it.copy(kitSort = sort) }
    }

    // ---------------------------------------------------------------------------
    // User actions — FAB / card taps
    // ---------------------------------------------------------------------------

    fun onAddLensTapped() = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToNewLens)
    }

    fun onLensCardTapped(lensId: Int) = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToLensDetail(lensId))
    }

    fun onAddBodyTapped() = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToNewBody)
    }

    fun onBodyCardTapped(bodyId: Int) = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToBodyDetail(bodyId))
    }

    fun onAddFilterTapped() = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToNewFilter)
    }

    fun onFilterCardTapped(filterId: Int) = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToFilterDetail(filterId))
    }

    fun onAddFilmStockTapped() = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToNewFilmStock)
    }

    fun onFilmStockCardTapped(filmStockId: Int) = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToFilmStockDetail(filmStockId))
    }

    fun onAddKitTapped() = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToNewKit)
    }

    fun onKitCardTapped(kitId: Int) = viewModelScope.launch {
        _events.send(GearLibraryEvent.NavigateToKitDetail(kitId))
    }
}

// ---------------------------------------------------------------------------
// Sort comparators
// ---------------------------------------------------------------------------

private val LensSort.comparator: Comparator<Lens>
    get() = when (this) {
        LensSort.NAME_ASC -> compareBy({ it.make }, { it.name })
        LensSort.MOUNT_TYPE -> compareBy({ it.mountType }, { it.make }, { it.name })
        // "Recently added" uses id desc — higher id = newer row
        LensSort.RECENTLY_ADDED -> compareByDescending { it.id }
    }

private val BodySort.comparator: Comparator<CameraBody>
    get() = when (this) {
        BodySort.NAME_ASC -> compareBy({ it.make }, { it.name })
        BodySort.RECENTLY_ADDED -> compareByDescending { it.id }
    }

private val FilterSort.comparator: Comparator<Filter>
    get() = when (this) {
        FilterSort.NAME_ASC -> compareBy({ it.filterType }, { it.name })
        // Null filter sizes sort last; smaller sizes first among non-null
        FilterSort.FILTER_SIZE -> Comparator { a, b ->
            when {
                a.filterSizeMm == null && b.filterSizeMm == null -> 0
                a.filterSizeMm == null -> 1
                b.filterSizeMm == null -> -1
                else -> a.filterSizeMm.compareTo(b.filterSizeMm)
            }
        }
        FilterSort.RECENTLY_ADDED -> compareByDescending { it.id }
    }

private val FilmStockSort.comparator: Comparator<FilmStock>
    get() = when (this) {
        FilmStockSort.NAME_ASC -> compareBy({ it.make }, { it.name })
        FilmStockSort.RECENTLY_ADDED -> compareByDescending { it.id }
    }

private val KitSort.comparator: Comparator<Kit>
    get() = when (this) {
        KitSort.NAME_ASC -> compareBy { it.name }
        // Null lastUsedAt sorts last (never used); most recent first
        KitSort.LAST_USED -> Comparator { a, b ->
            when {
                a.lastUsedAt == null && b.lastUsedAt == null -> 0
                a.lastUsedAt == null -> 1
                b.lastUsedAt == null -> -1
                else -> b.lastUsedAt.compareTo(a.lastUsedAt)
            }
        }
        KitSort.RECENTLY_ADDED -> compareByDescending { it.id }
    }

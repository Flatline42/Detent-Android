package com.southsouthwest.framelog.ui.gear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.FilmStock
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Kit
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.ui.navigation.CameraBodyDetail
import com.southsouthwest.framelog.ui.navigation.FilmStockDetail
import com.southsouthwest.framelog.ui.navigation.FilterDetail
import com.southsouthwest.framelog.ui.navigation.KitDetail
import com.southsouthwest.framelog.ui.navigation.LensDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearLibraryScreen(
    navController: NavController,
    vm: GearLibraryViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Translate ViewModel events to navigation actions
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                GearLibraryEvent.NavigateToNewLens ->
                    navController.navigate(LensDetail())
                is GearLibraryEvent.NavigateToLensDetail ->
                    navController.navigate(LensDetail(event.lensId))
                GearLibraryEvent.NavigateToNewBody ->
                    navController.navigate(CameraBodyDetail())
                is GearLibraryEvent.NavigateToBodyDetail ->
                    navController.navigate(CameraBodyDetail(event.bodyId))
                GearLibraryEvent.NavigateToNewFilter ->
                    navController.navigate(FilterDetail())
                is GearLibraryEvent.NavigateToFilterDetail ->
                    navController.navigate(FilterDetail(event.filterId))
                GearLibraryEvent.NavigateToNewFilmStock ->
                    navController.navigate(FilmStockDetail())
                is GearLibraryEvent.NavigateToFilmStockDetail ->
                    navController.navigate(FilmStockDetail(event.filmStockId))
                GearLibraryEvent.NavigateToNewKit ->
                    navController.navigate(KitDetail())
                is GearLibraryEvent.NavigateToKitDetail ->
                    navController.navigate(KitDetail(event.kitId))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Gear Library") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(state.selectedTab.fabLabel) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    when (state.selectedTab) {
                        GearTab.LENSES -> vm.onAddLensTapped()
                        GearTab.BODIES -> vm.onAddBodyTapped()
                        GearTab.FILTERS -> vm.onAddFilterTapped()
                        GearTab.FILM_STOCKS -> vm.onAddFilmStockTapped()
                        GearTab.KITS -> vm.onAddKitTapped()
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val tabs = GearTab.entries
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(state.selectedTab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { vm.onTabSelected(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (state.selectedTab) {
                GearTab.LENSES -> LensTab(state, vm)
                GearTab.BODIES -> BodyTab(state, vm)
                GearTab.FILTERS -> FilterTab(state, vm)
                GearTab.FILM_STOCKS -> FilmStockTab(state, vm)
                GearTab.KITS -> KitTab(state, vm)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab content composables
// ---------------------------------------------------------------------------

@Composable
private fun LensTab(state: GearLibraryUiState, vm: GearLibraryViewModel) {
    Column(Modifier.fillMaxSize()) {
        SearchAndSortRow(
            query = state.lensQuery,
            onQueryChange = vm::onLensQueryChanged,
            sortOptions = LensSort.entries,
            selectedSort = state.lensSort,
            onSortSelected = vm::onLensSortChanged,
            sortDisplayName = { it.label },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.lenses, key = { it.id }) { lens ->
                LensCard(lens, onClick = { vm.onLensCardTapped(lens.id) })
            }
            if (state.lenses.isEmpty() && !state.isLoading) {
                item { EmptyState("No lenses yet — tap + to add one") }
            }
        }
    }
}

@Composable
private fun BodyTab(state: GearLibraryUiState, vm: GearLibraryViewModel) {
    Column(Modifier.fillMaxSize()) {
        SearchAndSortRow(
            query = state.bodyQuery,
            onQueryChange = vm::onBodyQueryChanged,
            sortOptions = BodySort.entries,
            selectedSort = state.bodySort,
            onSortSelected = vm::onBodySortChanged,
            sortDisplayName = { it.label },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.bodies, key = { it.id }) { body ->
                BodyCard(body, onClick = { vm.onBodyCardTapped(body.id) })
            }
            if (state.bodies.isEmpty() && !state.isLoading) {
                item { EmptyState("No camera bodies yet — tap + to add one") }
            }
        }
    }
}

@Composable
private fun FilterTab(state: GearLibraryUiState, vm: GearLibraryViewModel) {
    Column(Modifier.fillMaxSize()) {
        SearchAndSortRow(
            query = state.filterQuery,
            onQueryChange = vm::onFilterQueryChanged,
            sortOptions = FilterSort.entries,
            selectedSort = state.filterSort,
            onSortSelected = vm::onFilterSortChanged,
            sortDisplayName = { it.label },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.filters, key = { it.id }) { filter ->
                FilterCard(filter, onClick = { vm.onFilterCardTapped(filter.id) })
            }
            if (state.filters.isEmpty() && !state.isLoading) {
                item { EmptyState("No filters yet — tap + to add one") }
            }
        }
    }
}

@Composable
private fun FilmStockTab(state: GearLibraryUiState, vm: GearLibraryViewModel) {
    Column(Modifier.fillMaxSize()) {
        SearchAndSortRow(
            query = state.filmStockQuery,
            onQueryChange = vm::onFilmStockQueryChanged,
            sortOptions = FilmStockSort.entries,
            selectedSort = state.filmStockSort,
            onSortSelected = vm::onFilmStockSortChanged,
            sortDisplayName = { it.label },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.filmStocks, key = { it.id }) { stock ->
                FilmStockCard(stock, onClick = { vm.onFilmStockCardTapped(stock.id) })
            }
            if (state.filmStocks.isEmpty() && !state.isLoading) {
                item { EmptyState("No film stocks yet — tap + to add one") }
            }
        }
    }
}

@Composable
private fun KitTab(state: GearLibraryUiState, vm: GearLibraryViewModel) {
    Column(Modifier.fillMaxSize()) {
        SearchAndSortRow(
            query = state.kitQuery,
            onQueryChange = vm::onKitQueryChanged,
            sortOptions = KitSort.entries,
            selectedSort = state.kitSort,
            onSortSelected = vm::onKitSortChanged,
            sortDisplayName = { it.label },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.kits, key = { it.id }) { kit ->
                KitCard(kit, onClick = { vm.onKitCardTapped(kit.id) })
            }
            if (state.kits.isEmpty() && !state.isLoading) {
                item { EmptyState("No kits yet — tap + to create one") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Gear list cards
// ---------------------------------------------------------------------------

@Composable
private fun LensCard(lens: Lens, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ListItem(
            headlineContent = { Text("${lens.focalLengthMm}mm ${lens.name}") },
            supportingContent = {
                Text("${lens.make} · ${lens.mountType} · f/${formatFloat(lens.maxAperture)}–${formatFloat(lens.minAperture)}")
            },
        )
    }
}

@Composable
private fun BodyCard(body: CameraBody, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ListItem(
            headlineContent = { Text(body.name) },
            supportingContent = {
                Text("${body.make} ${body.model} · ${body.format.value} · ${body.shutterIncrements.value} stop")
            },
        )
    }
}

@Composable
private fun FilterCard(filter: Filter, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val subtitle = buildString {
            append(filter.make)
            append(" · ")
            append(filter.filterType)
            filter.evReduction?.let { append(" · ${formatFloat(it)} EV") }
            filter.filterSizeMm?.let { append(" · ${it}mm") }
        }
        ListItem(
            headlineContent = { Text(filter.name) },
            supportingContent = { Text(subtitle) },
        )
    }
}

@Composable
private fun FilmStockCard(stock: FilmStock, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val subtitle = buildString {
            append("${stock.make} · ISO ${stock.iso} · ${stock.colorType.label}")
            if (stock.discontinued) append(" · discontinued")
        }
        ListItem(
            headlineContent = { Text(stock.name) },
            supportingContent = { Text(subtitle) },
        )
    }
}

@Composable
private fun KitCard(kit: Kit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val subtitle = kit.lastUsedAt?.let {
            "Last used ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it))}"
        } ?: "Never used"
        ListItem(
            headlineContent = { Text(kit.name) },
            supportingContent = { Text(subtitle) },
        )
    }
}

// ---------------------------------------------------------------------------
// Shared composables
// ---------------------------------------------------------------------------

/**
 * Search field + sort menu row shown at the top of each gear tab.
 * Generic over the sort option type so each tab can pass its own sort enum.
 */
@Composable
private fun <T> SearchAndSortRow(
    query: String,
    onQueryChange: (String) -> Unit,
    sortOptions: List<T>,
    selectedSort: T,
    onSortSelected: (T) -> Unit,
    sortDisplayName: (T) -> String,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search") },
            singleLine = true,
        )

        Box {
            TextButton(onClick = { showSortMenu = true }) {
                Text(sortDisplayName(selectedSort))
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                sortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortDisplayName(option)) },
                        onClick = { onSortSelected(option); showSortMenu = false },
                        trailingIcon = if (selectedSort == option) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message)
    }
}

// ---------------------------------------------------------------------------
// Display name helpers — extension properties on enums
// ---------------------------------------------------------------------------

private val GearTab.label: String
    get() = when (this) {
        GearTab.LENSES -> "Lenses"
        GearTab.BODIES -> "Bodies"
        GearTab.FILTERS -> "Filters"
        GearTab.FILM_STOCKS -> "Film"
        GearTab.KITS -> "Kits"
    }

private val GearTab.fabLabel: String
    get() = when (this) {
        GearTab.LENSES -> "Add Lens"
        GearTab.BODIES -> "Add Body"
        GearTab.FILTERS -> "Add Filter"
        GearTab.FILM_STOCKS -> "Add Film Stock"
        GearTab.KITS -> "Create Kit"
    }

private val LensSort.label: String
    get() = when (this) {
        LensSort.NAME_ASC -> "Name"
        LensSort.MOUNT_TYPE -> "Mount Type"
        LensSort.RECENTLY_ADDED -> "Recently Added"
    }

private val BodySort.label: String
    get() = when (this) {
        BodySort.NAME_ASC -> "Name"
        BodySort.RECENTLY_ADDED -> "Recently Added"
    }

private val FilterSort.label: String
    get() = when (this) {
        FilterSort.NAME_ASC -> "Name"
        FilterSort.FILTER_SIZE -> "Filter Size"
        FilterSort.RECENTLY_ADDED -> "Recently Added"
    }

private val FilmStockSort.label: String
    get() = when (this) {
        FilmStockSort.NAME_ASC -> "Name"
        FilmStockSort.RECENTLY_ADDED -> "Recently Added"
    }

private val KitSort.label: String
    get() = when (this) {
        KitSort.NAME_ASC -> "Name"
        KitSort.LAST_USED -> "Last Used"
        KitSort.RECENTLY_ADDED -> "Recently Added"
    }

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

/** Formats a float without trailing zeros: 1.4 → "1.4", 16.0 → "16". */
private fun formatFloat(f: Float): String =
    if (f == f.toLong().toFloat()) f.toLong().toString() else f.toString()

internal val com.southsouthwest.framelog.data.db.entity.ColorType.label: String
    get() = when (this) {
        com.southsouthwest.framelog.data.db.entity.ColorType.COLOR_NEGATIVE -> "Color Negative"
        com.southsouthwest.framelog.data.db.entity.ColorType.BW_NEGATIVE -> "B&W Negative"
        com.southsouthwest.framelog.data.db.entity.ColorType.SLIDE -> "Slide / Reversal"
    }

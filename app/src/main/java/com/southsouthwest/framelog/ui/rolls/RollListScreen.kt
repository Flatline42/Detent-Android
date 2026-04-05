package com.southsouthwest.framelog.ui.rolls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.ui.onboarding.OnboardingStep
import com.southsouthwest.framelog.ui.onboarding.OnboardingViewModel
import com.southsouthwest.framelog.data.db.entity.RollStatus
import com.southsouthwest.framelog.data.db.relation.RollListRow
import com.southsouthwest.framelog.ui.navigation.RollJournal
import com.southsouthwest.framelog.ui.navigation.RollSetup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollListScreen(
    navController: NavController,
    onboardingViewModel: OnboardingViewModel? = null,
    vm: RollListViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val onboardingStep by (onboardingViewModel?.step?.collectAsState()
        ?: remember { mutableStateOf(OnboardingStep.COMPLETE) })
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Pending confirmation dialogs — managed locally since confirmation happens before VM call
    var pendingLoadRoll by remember { mutableStateOf<Roll?>(null) }
    var pendingDeleteRoll by remember { mutableStateOf<Roll?>(null) }
    var pendingArchiveRoll by remember { mutableStateOf<Roll?>(null) }
    var pendingUnarchiveRoll by remember { mutableStateOf<Roll?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is RollListEvent.NavigateToRollSetup ->
                    navController.navigate(RollSetup)
                is RollListEvent.NavigateToRollJournal ->
                    navController.navigate(RollJournal(event.rollId))
                is RollListEvent.ShowLoadConfirmation ->
                    pendingLoadRoll = event.roll
                is RollListEvent.ShowDeleteConfirmation ->
                    pendingDeleteRoll = event.roll
                is RollListEvent.ShowErrorMessage ->
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
            }
        }
    }

    // Auto-select the Finished tab when onboarding reaches FINISHED_ROLLS_TOUR so the
    // coach card and spotlight appear on the correct tab immediately after navigation.
    LaunchedEffect(onboardingStep) {
        if (onboardingStep == OnboardingStep.FINISHED_ROLLS_TOUR) {
            vm.onTabSelected(RollListTab.FINISHED)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Rolls") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = vm::onAddRollTapped,
                modifier = Modifier.onGloballyPositioned { coords ->
                    if (onboardingStep == OnboardingStep.CREATE_ROLL) {
                        onboardingViewModel?.updateSpotlightBounds(coords.boundsInWindow())
                    }
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = "New roll")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Tab row — Active shows count, Finished/Archived show count only when non-zero
            val tabs = RollListTab.entries
            TabRow(selectedTabIndex = tabs.indexOf(state.selectedTab)) {
                Tab(
                    selected = state.selectedTab == RollListTab.ACTIVE,
                    onClick = { vm.onTabSelected(RollListTab.ACTIVE) },
                    text = { Text("Active (${state.activeRolls.size})") },
                )
                Tab(
                    selected = state.selectedTab == RollListTab.FINISHED,
                    onClick = { vm.onTabSelected(RollListTab.FINISHED) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        if (onboardingStep == OnboardingStep.FINISHED_ROLLS_TOUR) {
                            onboardingViewModel?.updateSpotlightBounds(coords.boundsInWindow())
                        }
                    },
                    text = {
                        val count = state.finishedRolls.size
                        Text(if (count > 0) "Finished ($count)" else "Finished")
                    },
                )
                Tab(
                    selected = state.selectedTab == RollListTab.ARCHIVED,
                    onClick = { vm.onTabSelected(RollListTab.ARCHIVED) },
                    text = {
                        val count = state.archivedRolls.size
                        Text(if (count > 0) "Archived ($count)" else "Archived")
                    },
                )
            }

            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::onSearchQueryChanged,
                placeholder = { Text("Search rolls…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            // Tab content
            when (state.selectedTab) {
                RollListTab.ACTIVE -> ActiveTabContent(
                    rows = state.activeRolls,
                    isLoading = state.isLoading,
                    onTap = { vm.onRollCardTapped(it) },
                    onLoadRollRequested = { vm.onLoadRollRequested(it) },
                    onDeleteTapped = { vm.onDeleteTapped(it) },
                )
                RollListTab.FINISHED -> FinishedTabContent(
                    rows = state.finishedRolls,
                    onTap = { vm.onRollCardTapped(it) },
                    onArchiveTapped = { pendingArchiveRoll = it },
                    onDeleteTapped = { vm.onDeleteTapped(it) },
                )
                RollListTab.ARCHIVED -> ArchivedTabContent(
                    rows = state.archivedRolls,
                    onTap = { vm.onRollCardTapped(it) },
                    onUnarchiveTapped = { pendingUnarchiveRoll = it },
                    onDeleteTapped = { vm.onDeleteTapped(it) },
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Confirmation dialogs
    // -------------------------------------------------------------------------

    pendingLoadRoll?.let { roll ->
        AlertDialog(
            onDismissRequest = { pendingLoadRoll = null },
            title = { Text("Load roll?") },
            text = { Text("\"${roll.name}\" will be loaded into your active camera queue.") },
            confirmButton = {
                TextButton(onClick = { vm.onLoadRollConfirmed(roll); pendingLoadRoll = null }) {
                    Text("Load")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLoadRoll = null }) { Text("Cancel") }
            },
        )
    }

    pendingDeleteRoll?.let { roll ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRoll = null },
            title = { Text("Delete roll?") },
            text = { Text("\"${roll.name}\" and all its frame data will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.onDeleteConfirmed(roll); pendingDeleteRoll = null },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRoll = null }) { Text("Cancel") }
            },
        )
    }

    pendingArchiveRoll?.let { roll ->
        AlertDialog(
            onDismissRequest = { pendingArchiveRoll = null },
            title = { Text("Archive roll?") },
            text = { Text("\"${roll.name}\" will be moved to the archive. You can unarchive it later.") },
            confirmButton = {
                TextButton(onClick = { vm.onArchiveConfirmed(roll); pendingArchiveRoll = null }) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchiveRoll = null }) { Text("Cancel") }
            },
        )
    }

    pendingUnarchiveRoll?.let { roll ->
        AlertDialog(
            onDismissRequest = { pendingUnarchiveRoll = null },
            title = { Text("Unarchive roll?") },
            text = { Text("\"${roll.name}\" will be moved back to Finished.") },
            confirmButton = {
                TextButton(onClick = { vm.onUnarchiveConfirmed(roll); pendingUnarchiveRoll = null }) {
                    Text("Unarchive")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnarchiveRoll = null }) { Text("Cancel") }
            },
        )
    }

}

// ---------------------------------------------------------------------------
// Tab content composables
// ---------------------------------------------------------------------------

@Composable
private fun ActiveTabContent(
    rows: List<RollListRow>,
    isLoading: Boolean,
    onTap: (rollId: Int) -> Unit,
    onLoadRollRequested: (Roll) -> Unit,
    onDeleteTapped: (Roll) -> Unit,
) {
    val loadedRows = rows.filter { it.roll.isLoaded }
    val unloadedRows = rows.filter { !it.roll.isLoaded }

    if (rows.isEmpty() && !isLoading) {
        EmptyRollState("No active rolls — tap + to create one")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp, vertical = 8.dp
        ),
    ) {
        if (loadedRows.isNotEmpty()) {
            item {
                SectionLabel("In camera")
            }
            items(loadedRows, key = { it.roll.id }) { row ->
                RollCard(
                    row = row,
                    onTap = { onTap(row.roll.id) },
                    onOpenJournal = { onTap(row.roll.id) },
                    onLoadRoll = null, // already loaded
                    onArchive = null,
                    onUnarchive = null,
                    onDelete = { onDeleteTapped(row.roll) },
                )
            }
        }

        if (unloadedRows.isNotEmpty()) {
            item {
                if (loadedRows.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                }
                SectionLabel("Inventory")
            }
            items(unloadedRows, key = { it.roll.id }) { row ->
                SwipeableRollCard(
                    row = row,
                    onSwipedToLoad = { onLoadRollRequested(row.roll) },
                    onTap = { onTap(row.roll.id) },
                    onLoadRoll = { onLoadRollRequested(row.roll) },
                    onDelete = { onDeleteTapped(row.roll) },
                )
            }
        }
    }
}

@Composable
private fun FinishedTabContent(
    rows: List<RollListRow>,
    onTap: (rollId: Int) -> Unit,
    onArchiveTapped: (Roll) -> Unit,
    onDeleteTapped: (Roll) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyRollState("No finished rolls")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp, vertical = 8.dp
        ),
    ) {
        items(rows, key = { it.roll.id }) { row ->
            RollCard(
                row = row,
                onTap = { onTap(row.roll.id) },
                onOpenJournal = { onTap(row.roll.id) },
                onLoadRoll = null,
                onArchive = { onArchiveTapped(row.roll) },
                onUnarchive = null,
                onDelete = { onDeleteTapped(row.roll) },
            )
        }
    }
}

@Composable
private fun ArchivedTabContent(
    rows: List<RollListRow>,
    onTap: (rollId: Int) -> Unit,
    onUnarchiveTapped: (Roll) -> Unit,
    onDeleteTapped: (Roll) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyRollState("No archived rolls")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp, vertical = 8.dp
        ),
    ) {
        items(rows, key = { it.roll.id }) { row ->
            RollCard(
                row = row,
                onTap = { onTap(row.roll.id) },
                onOpenJournal = { onTap(row.roll.id) },
                onLoadRoll = null,
                onArchive = null,
                onUnarchive = { onUnarchiveTapped(row.roll) },
                onDelete = { onDeleteTapped(row.roll) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Roll card with swipe-to-load wrapper (unloaded active rolls only)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRollCard(
    row: RollListRow,
    onSwipedToLoad: () -> Unit,
    onTap: () -> Unit,
    onLoadRoll: () -> Unit,
    onDelete: () -> Unit,
) {
    // confirmValueChange returns false so the card always snaps back to Settled.
    // The action is triggered as a side effect of the swipe completing.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onSwipedToLoad()
            }
            false // prevent card from staying in dismissed position
        },
        positionalThreshold = { it * 0.35f },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Revealed behind the card on either swipe direction
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "⟵  load roll  ⟶",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    ) {
        RollCard(
            row = row,
            onTap = onTap,
            onOpenJournal = onTap,
            onLoadRoll = onLoadRoll,
            onArchive = null,
            onUnarchive = null,
            onDelete = onDelete,
        )
    }
}

// ---------------------------------------------------------------------------
// Core roll card
// ---------------------------------------------------------------------------

/**
 * A single roll card. Visual treatment varies by roll state:
 *  - Loaded (active): solid border, normal text
 *  - Unloaded (active): dashed border, muted text, swipe hint
 *  - Finished / Archived: solid border, normal text
 *
 * Long press opens a state-aware context menu. Available menu items are driven by
 * which action lambdas are non-null.
 *
 * @param onLoadRoll Non-null only for unloaded active rolls.
 * @param onArchive Non-null only for finished rolls.
 * @param onUnarchive Non-null only for archived rolls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RollCard(
    row: RollListRow,
    onTap: () -> Unit,
    onOpenJournal: () -> Unit,
    onLoadRoll: (() -> Unit)?,
    onArchive: (() -> Unit)?,
    onUnarchive: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roll = row.roll
    val isUnloaded = !roll.isLoaded && roll.status == RollStatus.ACTIVE
    val isArchived = roll.status == RollStatus.ARCHIVED
    val textAlpha = if (isUnloaded || isArchived) 0.55f else 1f

    var showMenu by remember { mutableStateOf(false) }

    val outlineColor = MaterialTheme.colorScheme.outline

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isUnloaded) {
                        Modifier.dashedBorder(
                            color = outlineColor.copy(alpha = 0.5f),
                            cornerRadius = 12.dp,
                        )
                    } else {
                        Modifier.border(
                            width = 0.5.dp,
                            color = outlineColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                )
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = { showMenu = true },
                ),
            color = if (isArchived)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Leading status indicator box (placeholder for camera/canister icon)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (roll.isLoaded)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Roll name
                    Text(
                        text = roll.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(2.dp))

                    // Film stock + camera body
                    val stockAndBody = if (roll.isLoaded) {
                        "${row.filmStockMake} ${row.filmStockName}  ·  ${row.cameraBodyMake} ${row.cameraBodyName}"
                    } else {
                        "${row.filmStockMake} ${row.filmStockName}  ·  not loaded"
                    }
                    Text(
                        text = stockAndBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(2.dp))

                    // Date + ISO line
                    val dateLabel = when {
                        roll.isLoaded -> "Loaded ${roll.loadedAt.toFormattedDate()}"
                        roll.status == RollStatus.FINISHED ->
                            "Finished ${(roll.finishedAt ?: roll.loadedAt).toFormattedDate()}"
                        roll.status == RollStatus.ARCHIVED ->
                            "Finished ${(roll.finishedAt ?: roll.loadedAt).toFormattedDate()}"
                        else -> "Added ${roll.loadedAt.toFormattedDate()}"
                    }
                    Text(
                        text = "$dateLabel  ·  ISO ${roll.ratedISO}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                    )

                    Spacer(Modifier.height(6.dp))

                    // Progress bar + frame count
                    val progress = if (roll.totalExposures > 0) {
                        row.loggedFrameCount.toFloat() / roll.totalExposures
                    } else 0f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${row.loggedFrameCount} / ${roll.totalExposures}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                        )
                    }

                    // Swipe hint for unloaded rolls
                    if (isUnloaded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "← swipe to load →",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }

                    // Notes preview (first line only, italic)
                    val notesPreview = roll.notes?.lines()?.firstOrNull()?.trim()
                    if (!notesPreview.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "\"${notesPreview}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Long press context menu — state-aware options driven by which lambdas are non-null
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            onLoadRoll?.let {
                DropdownMenuItem(
                    text = { Text("Load roll") },
                    onClick = { it(); showMenu = false },
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text("Open journal") },
                onClick = { onOpenJournal(); showMenu = false },
            )
            onArchive?.let {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Archive") },
                    onClick = { it(); showMenu = false },
                )
            }
            onUnarchive?.let {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Unarchive") },
                    onClick = { it(); showMenu = false },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text("Delete roll", color = MaterialTheme.colorScheme.error)
                },
                onClick = { onDelete(); showMenu = false },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helper composables
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyRollState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private fun Long.toFormattedDate(): String = dateFormatter.format(Date(this))

/**
 * Draws a dashed rounded-rectangle border using [drawBehind].
 * Used for unloaded roll cards per the wireframe spec.
 */
private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp): Modifier =
    this.drawBehind {
        drawRoundRect(
            color = color,
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f),
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
    }

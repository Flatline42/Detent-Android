package com.southsouthwest.framelog.ui.rolls

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.southsouthwest.framelog.data.ExportFormat
import com.southsouthwest.framelog.ui.onboarding.OnboardingStep
import com.southsouthwest.framelog.ui.onboarding.OnboardingViewModel
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.data.db.entity.RollStatus
import com.southsouthwest.framelog.ui.navigation.FrameDetail
import com.southsouthwest.framelog.ui.util.ExposureValues
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Sort
// ---------------------------------------------------------------------------

private enum class FrameSort { ASCENDING, DESCENDING }

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollJournalScreen(
    navController: NavHostController,
    onboardingViewModel: OnboardingViewModel? = null,
) {
    val viewModel: RollJournalViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onboardingStep by (onboardingViewModel?.step?.collectAsState()
        ?: remember { mutableStateOf(OnboardingStep.COMPLETE) })
    val context = LocalContext.current

    // Local dialog visibility — set to true by one-shot events from the ViewModel
    var showLoadDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showUnarchiveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Local search + sort state — ephemeral UI state, not persisted in ViewModel
    var frameQuery by remember { mutableStateOf("") }
    var frameSort by remember { mutableStateOf(FrameSort.ASCENDING) }
    var showSortMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // One-shot event handler
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RollJournalEvent.NavigateToFrameDetail ->
                    navController.navigate(FrameDetail(event.frameId, event.rollId))
                is RollJournalEvent.NavigateBack ->
                    navController.popBackStack()
                is RollJournalEvent.ShowLoadRollConfirmation ->
                    showLoadDialog = true
                is RollJournalEvent.ShowFinishRollConfirmation ->
                    showFinishDialog = true
                is RollJournalEvent.ShowArchiveConfirmation ->
                    showArchiveDialog = true
                is RollJournalEvent.ShowUnarchiveConfirmation ->
                    showUnarchiveDialog = true
                is RollJournalEvent.ShowDeleteConfirmation ->
                    showDeleteDialog = true
                is RollJournalEvent.ShareExportContent -> {
                    val intent = if (event.mimeType == "text/csv") {
                        // Write CSV to cache so it can be shared as a file attachment
                        // via FileProvider (same authority used for database backup export).
                        val file = File(context.cacheDir, event.suggestedFilename)
                        file.writeText(event.content)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, event.suggestedFilename)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND).apply {
                            type = event.mimeType
                            putExtra(Intent.EXTRA_TEXT, event.content)
                            putExtra(Intent.EXTRA_SUBJECT, event.suggestedFilename)
                        }
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
                is RollJournalEvent.ShowErrorMessage ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val rollWithDetails = state.roll
    val rollEntity = rollWithDetails?.roll

    // Lens name lookup: lensId → display name for frame card subtitles
    val lensNameMap: Map<Int, String> = remember(rollWithDetails?.lenses) {
        rollWithDetails?.lenses?.associate { it.rollLens.lensId to it.lens.name } ?: emptyMap()
    }

    // Filter and sort the frame list based on local UI state
    val displayFrames = remember(rollWithDetails?.frames, frameQuery, frameSort) {
        val frames = rollWithDetails?.frames ?: emptyList()
        val filtered = if (frameQuery.isBlank()) {
            frames
        } else {
            frames.filter { frame ->
                frame.frameNumber.toString().contains(frameQuery) ||
                frame.aperture?.contains(frameQuery, ignoreCase = true) == true ||
                frame.shutterSpeed?.contains(frameQuery, ignoreCase = true) == true ||
                frame.notes?.contains(frameQuery, ignoreCase = true) == true ||
                lensNameMap[frame.lensId]?.contains(frameQuery, ignoreCase = true) == true
            }
        }
        if (frameSort == FrameSort.ASCENDING) filtered else filtered.reversed()
    }

    val loggedCount = rollWithDetails?.frames?.count { it.isLogged } ?: 0
    val totalExposures = rollEntity?.totalExposures ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rollEntity?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            if (rollEntity?.status == RollStatus.FINISHED) {
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.onArchiveTapped()
                                    },
                                )
                            }
                            if (rollEntity?.status == RollStatus.ARCHIVED) {
                                DropdownMenuItem(
                                    text = { Text("Unarchive") },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.onUnarchiveTapped()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.onDeleteTapped()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (rollEntity != null) {
                RollJournalBottomBar(
                    roll = rollEntity,
                    isActionInProgress = state.isActionInProgress,
                    onLoadRollTapped = viewModel::onLoadRollTapped,
                    onFinishRollTapped = viewModel::onFinishRollTapped,
                    onExportTapped = viewModel::onExportTapped,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Roll summary: subtitle (ISO, push/pull, status) + progress bar
                if (rollEntity != null) {
                    item {
                        RollJournalHeader(
                            roll = rollEntity,
                            loggedCount = loggedCount,
                            totalExposures = totalExposures,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // Search + sort row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = frameQuery,
                            onValueChange = { frameQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("search frames\u2026") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                        )
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Text("sort")
                                Icon(
                                    imageVector = if (frameSort == FrameSort.ASCENDING)
                                        Icons.Default.KeyboardArrowDown
                                    else
                                        Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Frame: 1 \u2192 $totalExposures") },
                                    onClick = {
                                        frameSort = FrameSort.ASCENDING
                                        showSortMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Frame: $totalExposures \u2192 1") },
                                    onClick = {
                                        frameSort = FrameSort.DESCENDING
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Frame cards
                items(displayFrames, key = { it.id }) { frame ->
                    FrameCard(
                        frame = frame,
                        lensName = lensNameMap[frame.lensId],
                        isCurrent = frame.frameNumber == state.currentFrameNumber,
                        onTapped = { viewModel.onFrameCardTapped(frame.id) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .then(
                                if (frame.frameNumber == 1 &&
                                    onboardingStep == OnboardingStep.ROLL_JOURNAL_TOUR
                                ) {
                                    Modifier.onGloballyPositioned { coords ->
                                        onboardingViewModel?.updateSpotlightBounds(
                                            coords.boundsInWindow(),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }

                // Empty state (e.g. search produced no results)
                if (displayFrames.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (frameQuery.isBlank()) "No frames"
                                       else "No frames match \u201c$frameQuery\u201d",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // Export format picker (shown as bottom sheet when state.showExportSheet = true)
    if (state.showExportSheet) {
        ExportFormatBottomSheet(
            selectedFormat = state.selectedExportFormat,
            onFormatSelected = viewModel::onExportFormatSelected,
            onExportConfirmed = viewModel::onExportConfirmed,
            onDismiss = viewModel::onExportDismissed,
        )
    }

    // Confirmation dialogs
    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text("Load roll?") },
            text = {
                Text(
                    "\"${rollEntity?.name ?: ""}\" will be marked as in camera. " +
                    "You can start logging frames once it\u2019s loaded.",
                )
            },
            confirmButton = {
                Button(onClick = { showLoadDialog = false; viewModel.onLoadRollConfirmed() }) {
                    Text("Load")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoadDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finish roll?") },
            text = {
                Text(
                    "\"${rollEntity?.name ?: ""}\" will be marked as finished and removed from " +
                    "the camera. This cannot be undone \u2014 a finished roll cannot return to active.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { showFinishDialog = false; viewModel.onFinishRollConfirmed() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Finish Roll")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text("Archive roll?") },
            text = { Text("\"${rollEntity?.name ?: ""}\" will be moved to your archive.") },
            confirmButton = {
                Button(onClick = { showArchiveDialog = false; viewModel.onArchiveConfirmed() }) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showUnarchiveDialog) {
        AlertDialog(
            onDismissRequest = { showUnarchiveDialog = false },
            title = { Text("Unarchive roll?") },
            text = {
                Text("\"${rollEntity?.name ?: ""}\" will be moved back to Finished.")
            },
            confirmButton = {
                Button(onClick = {
                    showUnarchiveDialog = false
                    viewModel.onUnarchiveConfirmed()
                }) {
                    Text("Unarchive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete roll?") },
            text = {
                Text(
                    "\"${rollEntity?.name ?: ""}\" and all its frame data will be permanently " +
                    "deleted. This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; viewModel.onDeleteConfirmed() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Header: subtitle + progress bar
// ---------------------------------------------------------------------------

@Composable
private fun RollJournalHeader(
    roll: Roll,
    loggedCount: Int,
    totalExposures: Int,
    modifier: Modifier = Modifier,
) {
    // Push/pull + ISO display following the handoff convention
    val isoLine = when (roll.pushPull) {
        null -> "ISO ${roll.ratedISO}"
        0 -> "ISO ${roll.ratedISO} \u00b7 box speed"
        else -> if (roll.pushPull > 0)
            "push ${roll.pushPull} \u00b7 ISO ${roll.ratedISO}"
        else
            "pull ${abs(roll.pushPull)} \u00b7 ISO ${roll.ratedISO}"
    }
    val statusLine = when {
        roll.status == RollStatus.FINISHED -> "finished"
        roll.status == RollStatus.ARCHIVED -> "archived"
        roll.isLoaded -> "in camera"
        else -> "not loaded"
    }

    Column(modifier = modifier) {
        Text(
            text = "$isoLine \u00b7 $statusLine",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = { loggedCount.toFloat() / totalExposures.coerceAtLeast(1) },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$loggedCount / $totalExposures",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Frame card
// ---------------------------------------------------------------------------

@Composable
private fun FrameCard(
    frame: Frame,
    lensName: String?,
    isCurrent: Boolean,
    onTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    // Border treatment: solid for logged, dashed for unlogged; accent color for current frame
    val cardModifier = when {
        isCurrent && frame.isLogged ->
            modifier.border(1.5.dp, accentColor, MaterialTheme.shapes.medium)
        isCurrent ->
            modifier.dashedBorder(accentColor, 12.dp)
        frame.isLogged ->
            modifier.border(0.5.dp, outlineColor, MaterialTheme.shapes.medium)
        else ->
            modifier.dashedBorder(outlineColor, 12.dp)
    }

    Card(
        onClick = onTapped,
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (frame.isLogged)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        // Zero elevation — border is the visual treatment
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FrameNumberCircle(
                frameNumber = frame.frameNumber,
                isLogged = frame.isLogged,
                isCurrent = isCurrent,
            )

            Column(modifier = Modifier.weight(1f)) {
                if (isCurrent) {
                    Text(
                        text = "CURRENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                if (frame.isLogged) {
                    LoggedFrameContent(frame = frame, lensName = lensName)
                } else {
                    Text(
                        text = "\u2014 unlogged \u2014",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
    }
}

@Composable
private fun FrameNumberCircle(
    frameNumber: Int,
    isLogged: Boolean,
    isCurrent: Boolean,
) {
    val accentColor = MaterialTheme.colorScheme.tertiary
    val fillColor = when {
        isCurrent -> accentColor
        isLogged -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isCurrent -> MaterialTheme.colorScheme.onTertiary
        isLogged -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fillColor)
            .border(1.dp, if (isCurrent) accentColor else MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = frameNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

@Composable
private fun LoggedFrameContent(
    frame: Frame,
    lensName: String?,
) {
    // Aperture + shutter speed
    if (frame.aperture != null || frame.shutterSpeed != null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (frame.aperture != null) {
                Text(
                    text = frame.aperture,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (frame.shutterSpeed != null) {
                val displayed = ExposureValues.shutterDisplayValue(frame.shutterSpeed)
                val isLong = ExposureValues.isLongExposure(frame.shutterSpeed)
                Text(
                    text = displayed,
                    style = MaterialTheme.typography.bodyMedium,
                    // Long exposures (whole seconds + B) rendered in accent/error color per film
                    // camera convention (red numbers on cameras like Canon AE-1)
                    color = if (isLong) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    // Lens name
    if (lensName != null) {
        Text(
            text = lensName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    // Exposure compensation + GPS indicator
    val hasEC = frame.exposureCompensation != null
    val hasGps = frame.lat != null && frame.lng != null
    if (hasEC || hasGps) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            if (hasEC) {
                Text(
                    text = "EC ${ExposureValues.formatExposureCompensation(frame.exposureCompensation!!)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasGps) {
                Text(
                    text = "GPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Notes preview (first line)
    if (!frame.notes.isNullOrBlank()) {
        Text(
            text = frame.notes.lines().first(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom bar
// ---------------------------------------------------------------------------

@Composable
private fun RollJournalBottomBar(
    roll: Roll,
    isActionInProgress: Boolean,
    onLoadRollTapped: () -> Unit,
    onFinishRollTapped: () -> Unit,
    onExportTapped: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            roll.status == RollStatus.ACTIVE && !roll.isLoaded -> {
                // Inventory roll: can be loaded
                Button(
                    onClick = onLoadRollTapped,
                    modifier = Modifier.weight(1f),
                    enabled = !isActionInProgress,
                ) {
                    Text("Load Roll")
                }
                OutlinedButton(
                    onClick = onExportTapped,
                    modifier = Modifier.weight(1f),
                    enabled = !isActionInProgress,
                ) {
                    Text("Export")
                }
            }
            roll.status == RollStatus.ACTIVE && roll.isLoaded -> {
                // In-camera roll: can be finished
                Button(
                    onClick = onFinishRollTapped,
                    modifier = Modifier.weight(1f),
                    enabled = !isActionInProgress,
                ) {
                    Text("Finish Roll")
                }
                OutlinedButton(
                    onClick = onExportTapped,
                    modifier = Modifier.weight(1f),
                    enabled = !isActionInProgress,
                ) {
                    Text("Export")
                }
            }
            else -> {
                // Finished or archived: export only
                OutlinedButton(
                    onClick = onExportTapped,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isActionInProgress,
                ) {
                    Text("Export")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Export bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatBottomSheet(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    onExportConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Export",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            ExportFormat.entries.forEach { format ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFormatSelected(format) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedFormat == format,
                        onClick = { onFormatSelected(format) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(format.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onExportConfirmed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export")
            }
        }
    }
}

private val ExportFormat.label: String
    get() = when (this) {
        ExportFormat.CSV -> "CSV"
        ExportFormat.JSON -> "JSON"
        ExportFormat.PLAIN_TEXT -> "Plain Text"
    }

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Draws a dashed rectangular border behind the composable. Used for unlogged frame cards
 * and inventory roll cards (not yet in a camera) to distinguish them from loaded/logged items.
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

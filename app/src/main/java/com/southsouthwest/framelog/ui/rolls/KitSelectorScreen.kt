package com.southsouthwest.framelog.ui.rolls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.southsouthwest.framelog.data.db.relation.KitWithDetails
import com.southsouthwest.framelog.ui.navigation.KitDetail

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

/**
 * Presents a searchable list of saved kits. On selection, writes the selected
 * kit ID to the previous back stack entry's SavedStateHandle under "selected_kit_id",
 * then pops back. [RollSetupViewModel] observes that key and pre-populates the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitSelectorScreen(navController: NavHostController) {
    val viewModel: KitSelectorViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is KitSelectorEvent.KitSelected -> {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("selected_kit_id", event.kit.kit.id)
                    navController.popBackStack()
                }
                is KitSelectorEvent.NavigateToNewKit ->
                    navController.navigate(KitDetail(0))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Kit") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onCreateNewKitTapped) {
                Icon(Icons.Default.Add, contentDescription = "New kit")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search kits\u2026") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.kits.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.searchQuery.isBlank()) "No kits saved yet"
                               else "No kits match \u201c${state.searchQuery}\u201d",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(state.kits, key = { it.kit.id }) { kit ->
                        KitSelectorCard(
                            kit = kit,
                            onClick = { viewModel.onKitSelected(kit) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Kit card
// ---------------------------------------------------------------------------

@Composable
private fun KitSelectorCard(
    kit: KitWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = kit.kit.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${kit.cameraBody.make} ${kit.cameraBody.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Lens summary
            if (kit.lenses.isNotEmpty()) {
                val primaryLens = kit.lenses.firstOrNull { it.kitLens.isPrimary }?.lens
                    ?: kit.lenses.first().lens
                val extraCount = kit.lenses.size - 1
                val lensText = buildString {
                    append(primaryLens.name)
                    if (extraCount > 0) append(" +$extraCount more")
                }
                Text(
                    text = lensText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Filter summary
            if (kit.filters.isNotEmpty()) {
                Text(
                    text = kit.filters.joinToString(" \u00b7 ") { it.filter.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

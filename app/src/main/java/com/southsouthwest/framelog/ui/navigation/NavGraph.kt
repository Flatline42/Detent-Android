package com.southsouthwest.framelog.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.ui.frames.FrameDetailScreen
import com.southsouthwest.framelog.ui.gear.CameraBodyDetailScreen
import com.southsouthwest.framelog.ui.gear.FilmStockDetailScreen
import com.southsouthwest.framelog.ui.gear.FilterDetailScreen
import com.southsouthwest.framelog.ui.gear.GearLibraryScreen
import com.southsouthwest.framelog.ui.gear.KitDetailScreen
import com.southsouthwest.framelog.ui.gear.LensDetailScreen
import com.southsouthwest.framelog.ui.quickscreen.QuickScreenScreen
import com.southsouthwest.framelog.ui.rolls.KitSelectorScreen
import com.southsouthwest.framelog.ui.rolls.RollJournalScreen
import com.southsouthwest.framelog.ui.rolls.RollListScreen
import com.southsouthwest.framelog.ui.rolls.RollSetupScreen
import com.southsouthwest.framelog.ui.settings.SettingsScreen

/**
 * Root navigation graph for FRAME//LOG.
 *
 * A root [Scaffold] hosts the 5-tab [NavigationBar]. The bar is shown only on the five
 * top-level destinations; detail screens (LensDetail, FrameDetail, etc.) hide it automatically.
 *
 * [Modifier.consumeWindowInsets] is applied to the [NavHost] wrapper so that each screen's
 * own [Scaffold] can still consume insets independently without double-padding.
 *
 * Start destination is [QuickScreen] — the app's daily-use home. On first launch, the
 * onboarding flow will gate this (TODO: implement onboarding launch check).
 */
@Composable
fun FrameLogNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Bottom bar is visible only on the five top-level tab destinations.
    val showBottomBar = currentDestination != null && (
        currentDestination.hasRoute<GearLibrary>() ||
        currentDestination.hasRoute<RollList>() ||
        currentDestination.hasRoute<QuickScreen>() ||
        currentDestination.hasRoute<RollJournal>() ||
        currentDestination.hasRoute<Settings>()
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                FrameLogNavigationBar(
                    navController = navController,
                    currentDestination = currentDestination,
                )
            }
        },
    ) { innerPadding ->
        // consumeWindowInsets tells the composition tree that innerPadding has been accounted
        // for, so each screen's own Scaffold does not re-apply the same insets.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets(bottom = innerPadding.calculateBottomPadding()))
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            NavHost(
                navController = navController,
                startDestination = QuickScreen,
            ) {

                // ------------------------------------------------------------------
                // Gear Library
                // ------------------------------------------------------------------

                composable<GearLibrary> {
                    GearLibraryScreen(navController = navController)
                }

                composable<LensDetail> {
                    LensDetailScreen(navController = navController)
                }

                composable<CameraBodyDetail> {
                    CameraBodyDetailScreen(navController = navController)
                }

                composable<FilterDetail> {
                    FilterDetailScreen(navController = navController)
                }

                composable<FilmStockDetail> {
                    FilmStockDetailScreen(navController = navController)
                }

                composable<KitDetail> {
                    KitDetailScreen(navController = navController)
                }

                // ------------------------------------------------------------------
                // Rolls
                // ------------------------------------------------------------------

                composable<RollList> {
                    RollListScreen(navController = navController)
                }

                composable<RollJournal> {
                    RollJournalScreen(navController = navController)
                }

                composable<RollSetup> {
                    RollSetupScreen(navController = navController)
                }

                composable<KitSelector> {
                    KitSelectorScreen(navController = navController)
                }

                composable<FrameDetail> {
                    FrameDetailScreen(navController = navController)
                }

                // ------------------------------------------------------------------
                // Top-level tabs
                // ------------------------------------------------------------------

                composable<QuickScreen> {
                    QuickScreenScreen(navController = navController)
                }

                composable<Settings> {
                    SettingsScreen(navController = navController)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom navigation bar
// ---------------------------------------------------------------------------

/**
 * Five-tab [NavigationBar] for FRAME//LOG.
 *
 * Tab 4 (Journal) navigates to the currently active loaded roll's journal.
 * If no roll is loaded ([AppPreferences.activeRollId] == -1), it falls back to [RollList]
 * so the user can load a roll first.
 *
 * All tab navigations use [popUpTo] QuickScreen + [launchSingleTop] + [restoreState] so
 * that tapping between tabs does not build a deep back stack and state is preserved
 * when returning to a previously visited tab.
 */
@Composable
private fun FrameLogNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
    val context = LocalContext.current

    NavigationBar {
        // 1 — Gear Library
        NavigationBarItem(
            icon = { Icon(Icons.Default.Build, contentDescription = null) },
            label = { Text("Gear") },
            selected = currentDestination?.hasRoute<GearLibrary>() ?: false,
            onClick = {
                navController.navigate(GearLibrary) {
                    popUpTo<QuickScreen> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )

        // 2 — Roll List
        NavigationBarItem(
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text("Rolls") },
            selected = currentDestination?.hasRoute<RollList>() ?: false,
            onClick = {
                navController.navigate(RollList) {
                    popUpTo<QuickScreen> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )

        // 3 — Quick Screen (center / home)
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Quick") },
            selected = currentDestination?.hasRoute<QuickScreen>() ?: false,
            onClick = {
                navController.navigate(QuickScreen) {
                    popUpTo<QuickScreen> { inclusive = true; saveState = false }
                    launchSingleTop = true
                }
            },
        )

        // 4 — Active Roll Journal
        NavigationBarItem(
            icon = { Icon(Icons.Default.Create, contentDescription = null) },
            label = { Text("Journal") },
            selected = currentDestination?.hasRoute<RollJournal>() ?: false,
            onClick = {
                val activeRollId = AppPreferences(context).activeRollId
                if (activeRollId != -1) {
                    navController.navigate(RollJournal(activeRollId)) {
                        popUpTo<QuickScreen> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                    // No loaded roll — send to Roll List so the user can load one
                    navController.navigate(RollList) {
                        popUpTo<QuickScreen> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
        )

        // 5 — Settings
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = currentDestination?.hasRoute<Settings>() ?: false,
            onClick = {
                navController.navigate(Settings) {
                    popUpTo<QuickScreen> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

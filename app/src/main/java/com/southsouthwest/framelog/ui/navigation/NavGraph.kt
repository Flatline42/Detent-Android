package com.southsouthwest.framelog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.southsouthwest.framelog.ui.gear.CameraBodyDetailScreen
import com.southsouthwest.framelog.ui.gear.FilmStockDetailScreen
import com.southsouthwest.framelog.ui.gear.FilterDetailScreen
import com.southsouthwest.framelog.ui.gear.GearLibraryScreen
import com.southsouthwest.framelog.ui.gear.KitDetailScreen
import com.southsouthwest.framelog.ui.gear.LensDetailScreen
import com.southsouthwest.framelog.ui.rolls.RollJournalScreen
import com.southsouthwest.framelog.ui.rolls.RollListScreen

/**
 * Root navigation graph for FRAME//LOG.
 *
 * Temporary start destination is [GearLibrary] while the Gear Library screens are being
 * built out. This will change to [QuickScreen] once the full app launch sequence is
 * implemented (onboarding check → loaded roll check → Quick Screen or empty state).
 *
 * Each composable block creates its own ViewModel via viewModel(). Navigation Compose 2.8.0
 * automatically populates SavedStateHandle with the typed route parameters, so ViewModels
 * that read savedState["id"] receive the correct value from the route.
 */
@Composable
fun FrameLogNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = GearLibrary,
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

        // composable<RollSetup> { RollSetupScreen(navController) }  — TODO next session
        // composable<KitSelector> { KitSelectorScreen(navController) }  — TODO next session
        // composable<FrameDetail> { FrameDetailScreen(navController) }  — TODO next session

        // ------------------------------------------------------------------
        // Other root screens — TODO: implement in next session
        // ------------------------------------------------------------------

        // composable<QuickScreen> { QuickScreen(navController) }
        // composable<Settings> { SettingsScreen(navController) }
    }
}

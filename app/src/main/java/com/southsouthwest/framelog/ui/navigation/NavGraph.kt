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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.ui.frames.FrameDetailScreen
import com.southsouthwest.framelog.ui.gear.CameraBodyDetailScreen
import com.southsouthwest.framelog.ui.gear.FilmStockDetailScreen
import com.southsouthwest.framelog.ui.gear.FilterDetailScreen
import com.southsouthwest.framelog.ui.gear.GearLibraryScreen
import com.southsouthwest.framelog.ui.gear.KitDetailScreen
import com.southsouthwest.framelog.ui.gear.LensDetailScreen
import com.southsouthwest.framelog.ui.onboarding.OnboardingCoachOverlay
import com.southsouthwest.framelog.ui.onboarding.OnboardingStep
import com.southsouthwest.framelog.ui.onboarding.OnboardingViewModel
import com.southsouthwest.framelog.ui.onboarding.WelcomeScreen
import com.southsouthwest.framelog.ui.onboarding.WidgetSetupScreen
import com.southsouthwest.framelog.ui.quickscreen.QuickScreenScreen
import com.southsouthwest.framelog.ui.rolls.KitSelectorScreen
import com.southsouthwest.framelog.ui.rolls.RollJournalScreen
import com.southsouthwest.framelog.ui.rolls.RollListScreen
import com.southsouthwest.framelog.ui.rolls.RollSetupScreen
import com.southsouthwest.framelog.ui.settings.SettingsScreen

/**
 * Root navigation graph for DETENT.
 *
 * A root [Scaffold] hosts the 5-tab [NavigationBar]. The bar is shown only on the five
 * top-level destinations; detail screens (LensDetail, FrameDetail, etc.) hide it automatically.
 * Welcome and WidgetSetup also hide it.
 *
 * [Modifier.consumeWindowInsets] is applied to the [NavHost] wrapper so that each screen's
 * own [Scaffold] can still consume insets independently without double-padding.
 *
 * **Start destination** is determined at composition time from [AppPreferences.onboardingComplete]:
 *   - false → [Welcome] (onboarding flow begins)
 *   - true  → [QuickScreen] (normal daily use)
 *
 * **Onboarding overlay** — [OnboardingCoachOverlay] is rendered in a [Box] on top of the
 * [NavHost] whenever [onboardingStep.showsOverlay] is true AND the current destination is
 * not a form/detail screen (the user must complete a form action without the overlay blocking).
 *
 * @param navController Shared [NavHostController] from [MainActivity].
 * @param onboardingViewModel [OnboardingViewModel] created in [MainActivity]; holds the
 *   current onboarding step and exposes advance/skip/complete actions.
 */
@Composable
fun FrameLogNavGraph(
    navController: NavHostController,
    onboardingViewModel: OnboardingViewModel,
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Collect the current onboarding step so we can show/hide the overlay reactively.
    val onboardingStep by onboardingViewModel.step.collectAsState()
    val spotlightBounds by onboardingViewModel.spotlightBounds.collectAsState()
    val tabRowBounds by onboardingViewModel.tabRowBounds.collectAsState()

    // Determine start destination once at composition time.
    // This is a remember so it doesn't change on recompositions (e.g. theme change).
    val startDestination: Any = remember {
        if (AppPreferences(context).onboardingComplete) QuickScreen else Welcome
    }

    // Bottom bar is visible only on the five top-level tab destinations.
    // Welcome and WidgetSetup don't match any of these routes, so they are naturally excluded.
    val showBottomBar = currentDestination != null && (
        currentDestination.hasRoute<GearLibrary>() ||
        currentDestination.hasRoute<RollList>() ||
        currentDestination.hasRoute<QuickScreen>() ||
        currentDestination.hasRoute<RollJournal>() ||
        currentDestination.hasRoute<Settings>()
    )

    // ── Step-driven navigation side-effects ──────────────────────────────────
    //
    // These LaunchedEffects watch the onboarding step and fire navigation commands
    // as the user advances through the coach flow. Navigation is kept here (in NavGraph)
    // rather than in the ViewModel because NavController is a UI concern.

    // Primary step → navigation mapping.
    LaunchedEffect(onboardingStep) {
        when (onboardingStep) {
            OnboardingStep.ROLL_JOURNAL_TOUR -> {
                // Read activeRollId from AppPreferences. The step advances here only after the
                // user has created & loaded a roll (see the CREATE_ROLL + QuickScreen watcher below).
                val rollId = AppPreferences(context).activeRollId
                if (rollId > 0) {
                    navController.navigate(RollJournal(rollId)) {
                        launchSingleTop = true
                    }
                } else {
                    // No roll was created (user skipped or something went wrong) — skip this tour step.
                    onboardingViewModel.advance()
                }
            }
            OnboardingStep.WIDGET_SETUP_SCREEN -> {
                navController.navigate(WidgetSetup(fromOnboarding = true)) {
                    launchSingleTop = true
                }
            }
            OnboardingStep.COMPLETE -> {
                navController.navigate(QuickScreen) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            else -> {
                // Other step transitions do not require automatic navigation here.
            }
        }
    }

    // When the user is on CREATE_ROLL step and navigates to QuickScreen (meaning they just
    // created & loaded a roll), advance the step to ROLL_JOURNAL_TOUR. The primary
    // LaunchedEffect(onboardingStep) will then navigate to the roll journal.
    LaunchedEffect(onboardingStep, currentDestination) {
        if (onboardingStep == OnboardingStep.CREATE_ROLL &&
            currentDestination?.hasRoute<QuickScreen>() == true
        ) {
            onboardingViewModel.advance() // → ROLL_JOURNAL_TOUR
        }
    }

    // Tertiary: when the user navigates back to Welcome from Settings (resetToWelcome),
    // ensure the ViewModel step is reset if it was in COMPLETE state.
    LaunchedEffect(currentDestination) {
        if (currentDestination?.hasRoute<Welcome>() == true &&
            onboardingViewModel.step.value == OnboardingStep.COMPLETE
        ) {
            onboardingViewModel.resetToWelcome()
        }
    }

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
            // ── NavHost ─────────────────────────────────────────────────────
            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {

                // ------------------------------------------------------------------
                // Onboarding screens
                // ------------------------------------------------------------------

                composable<Welcome> {
                    WelcomeScreen(
                        onShowMeAround = {
                            // Advance WELCOME → ADD_LENS, then navigate to GearLibrary.
                            // popUpTo Welcome inclusive so back doesn't return here.
                            onboardingViewModel.advance()
                            navController.navigate(GearLibrary) {
                                popUpTo<Welcome> { inclusive = true }
                            }
                        },
                        onSkipSetup = {
                            onboardingViewModel.skip()
                            navController.navigate(QuickScreen) {
                                popUpTo<Welcome> { inclusive = true }
                            }
                        },
                    )
                }

                composable<WidgetSetup> {
                    val args = it.toRoute<WidgetSetup>()
                    WidgetSetupScreen(
                        navController = navController,
                        fromOnboarding = args.fromOnboarding,
                        onboardingViewModel = onboardingViewModel,
                    )
                }

                // ------------------------------------------------------------------
                // Gear Library
                // ------------------------------------------------------------------

                composable<GearLibrary> {
                    GearLibraryScreen(
                        navController = navController,
                        onboardingViewModel = onboardingViewModel,
                    )
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
                    RollListScreen(
                        navController = navController,
                        onboardingViewModel = onboardingViewModel,
                    )
                }

                composable<RollJournal>(
                    deepLinks = listOf(navDeepLink { uriPattern = "detent://journal/{rollId}" }),
                ) {
                    RollJournalScreen(
                        navController = navController,
                        onboardingViewModel = onboardingViewModel,
                    )
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
                    QuickScreenScreen(
                        navController = navController,
                        onboardingViewModel = onboardingViewModel,
                    )
                }

                composable<Settings> {
                    SettingsScreen(navController = navController)
                }
            }

            // ── Onboarding coach overlay ─────────────────────────────────────
            //
            // Rendered on top of the NavHost when:
            //   1. The current step has showsOverlay = true, AND
            //   2. The current destination is NOT a form/detail screen (we hide the overlay
            //      while the user fills in a form so the card doesn't block input fields).
            if (onboardingStep.showsOverlay) {
                val isOnFormScreen = currentDestination != null && (
                    currentDestination.hasRoute<LensDetail>() ||
                    currentDestination.hasRoute<CameraBodyDetail>() ||
                    currentDestination.hasRoute<FilterDetail>() ||
                    currentDestination.hasRoute<FilmStockDetail>() ||
                    currentDestination.hasRoute<KitDetail>() ||
                    currentDestination.hasRoute<RollSetup>() ||
                    currentDestination.hasRoute<KitSelector>() ||
                    currentDestination.hasRoute<FrameDetail>()
                )

                if (!isOnFormScreen) {
                    OnboardingCoachOverlay(
                        step = onboardingStep,
                        spotlightBounds = spotlightBounds,
                        tabRowBounds = tabRowBounds,
                        onGotIt = {
                            // For "do" steps, navigate to the relevant form so the user can
                            // take the real action (adding gear, creating a roll, etc.).
                            // The overlay hides automatically when isOnFormScreen becomes true.
                            // Advance the step AFTER initiating navigation so the overlay
                            // disappears in sync with the destination change.
                            when (onboardingStep) {
                                OnboardingStep.ADD_LENS -> {
                                    navController.navigate(LensDetail(0))
                                    onboardingViewModel.advance()
                                }
                                OnboardingStep.ADD_BODY -> {
                                    navController.navigate(CameraBodyDetail(0))
                                    onboardingViewModel.advance()
                                }
                                OnboardingStep.ADD_FILM_STOCK -> {
                                    navController.navigate(FilmStockDetail(0))
                                    onboardingViewModel.advance()
                                }
                                OnboardingStep.CREATE_ROLL -> {
                                    // Navigate to RollSetup; do NOT advance the step here.
                                    // The step advances automatically when the user creates &
                                    // loads a roll and the destination becomes QuickScreen
                                    // (see the CREATE_ROLL + QuickScreen LaunchedEffect above).
                                    navController.navigate(RollSetup)
                                }
                                OnboardingStep.KITS_TOUR -> {
                                    // Navigate to RollList before advancing to CREATE_ROLL.
                                    navController.navigate(RollList) {
                                        popUpTo<GearLibrary> { inclusive = false }
                                    }
                                    onboardingViewModel.advance()
                                }
                                OnboardingStep.FINISHED_ROLLS_TOUR -> {
                                    // Advance to WIDGET_SETUP_SCREEN; the LaunchedEffect above
                                    // handles the navigation to WidgetSetup(fromOnboarding=true).
                                    onboardingViewModel.advance()
                                }
                                else -> {
                                    // Tour/informational steps — just advance the step counter.
                                    onboardingViewModel.advance()
                                }
                            }
                        },
                        onSkip = {
                            onboardingViewModel.skip()
                            navController.navigate(QuickScreen) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom navigation bar
// ---------------------------------------------------------------------------

/**
 * Five-tab [NavigationBar] for DETENT.
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
                    // No restoreState: always land on RollList itself, not a saved sub-stack
                    // that may include RollJournal stacked on top (navigated there via card tap).
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

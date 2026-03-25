package com.southsouthwest.framelog.ui.onboarding

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import com.southsouthwest.framelog.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which bottom navigation tab should receive the onboarding highlight ring.
 * Null-mapped steps (WELCOME, WIDGET_SETUP_SCREEN, COMPLETE) produce no highlight.
 */
enum class OnboardingNavTab { GEAR, ROLLS, QUICK, JOURNAL, SETTINGS }

/**
 * ViewModel that owns onboarding progression state.
 *
 * Lives at the NavGraph level (created in MainActivity and passed down) so it persists
 * across destination changes during the coached flow. It does NOT survive process death —
 * onboarding state is recovered on next launch from [AppPreferences.onboardingComplete].
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val appPreferences = AppPreferences(application)

    private val _step = MutableStateFlow(
        if (appPreferences.onboardingComplete) OnboardingStep.COMPLETE else OnboardingStep.WELCOME,
    )
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    private val _spotlightBounds = MutableStateFlow<Rect?>(null)
    val spotlightBounds: StateFlow<Rect?> = _spotlightBounds.asStateFlow()

    private val _tabRowBounds = MutableStateFlow<Rect?>(null)
    val tabRowBounds: StateFlow<Rect?> = _tabRowBounds.asStateFlow()

    // ---------------------------------------------------------------------------
    // Step sequencing
    // ---------------------------------------------------------------------------

    /**
     * Returns the step that follows [step] in the linear coach mark sequence.
     * [OnboardingStep.COMPLETE] maps to itself — callers should guard against
     * calling advance() on a completed flow.
     */
    private fun nextStep(step: OnboardingStep): OnboardingStep = when (step) {
        OnboardingStep.WELCOME -> OnboardingStep.ADD_LENS
        OnboardingStep.ADD_LENS -> OnboardingStep.ADD_BODY
        OnboardingStep.ADD_BODY -> OnboardingStep.FILTERS_TOUR
        OnboardingStep.FILTERS_TOUR -> OnboardingStep.ADD_FILM_STOCK
        OnboardingStep.ADD_FILM_STOCK -> OnboardingStep.KITS_TOUR
        OnboardingStep.KITS_TOUR -> OnboardingStep.CREATE_ROLL
        OnboardingStep.CREATE_ROLL -> OnboardingStep.ROLL_JOURNAL_TOUR
        OnboardingStep.ROLL_JOURNAL_TOUR -> OnboardingStep.QS_HEADER
        OnboardingStep.QS_HEADER -> OnboardingStep.QS_LENS
        OnboardingStep.QS_LENS -> OnboardingStep.QS_FILTERS
        OnboardingStep.QS_FILTERS -> OnboardingStep.QS_STEPPERS
        OnboardingStep.QS_STEPPERS -> OnboardingStep.QS_FRAME
        OnboardingStep.QS_FRAME -> OnboardingStep.QS_LOG_FRAME
        OnboardingStep.QS_LOG_FRAME -> OnboardingStep.FINISHED_ROLLS_TOUR
        OnboardingStep.FINISHED_ROLLS_TOUR -> OnboardingStep.WIDGET_SETUP_SCREEN
        OnboardingStep.WIDGET_SETUP_SCREEN -> OnboardingStep.COMPLETE
        OnboardingStep.COMPLETE -> OnboardingStep.COMPLETE
    }

    // ---------------------------------------------------------------------------
    // Public actions
    // ---------------------------------------------------------------------------

    /** Called by target UI elements via onGloballyPositioned to report their current screen bounds. */
    fun updateSpotlightBounds(bounds: Rect) {
        _spotlightBounds.value = bounds
    }

    /** Called by the GearLibrary ScrollableTabRow to report its bounds (used to cut it out of the scrim). */
    fun updateTabRowBounds(bounds: Rect) {
        _tabRowBounds.value = bounds
    }

    /**
     * Advance to the next step in the coach mark sequence.
     * Has no effect when already [OnboardingStep.COMPLETE].
     */
    fun advance() {
        _spotlightBounds.value = null
        _step.value = nextStep(_step.value)
    }

    /**
     * Skip the entire onboarding flow and mark it complete immediately.
     * The NavGraph will react to the COMPLETE state and navigate to QuickScreen.
     */
    fun skip() {
        appPreferences.onboardingComplete = true
        _step.value = OnboardingStep.COMPLETE
    }

    /**
     * Called from [WidgetSetupScreen] when the user taps "Done" at the end of onboarding.
     * Marks onboarding complete in SharedPreferences.
     * The NavGraph reacts to the COMPLETE state and navigates to QuickScreen.
     */
    fun complete() {
        appPreferences.onboardingComplete = true
        _step.value = OnboardingStep.COMPLETE
    }

    /**
     * Called when the user triggers "Re-run introduction" from Settings.
     * Resets [AppPreferences.onboardingComplete] to false and returns to the WELCOME step
     * so the full flow can be walked through again.
     *
     * The NavGraph navigates back to [Welcome] when it sees WELCOME step on the Settings back
     * destination — see the LaunchedEffect(currentDestination) in NavGraph.
     */
    fun resetToWelcome() {
        appPreferences.onboardingComplete = false
        _spotlightBounds.value = null
        _tabRowBounds.value = null
        _step.value = OnboardingStep.WELCOME
    }
}

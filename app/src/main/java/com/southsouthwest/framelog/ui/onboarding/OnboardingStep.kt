package com.southsouthwest.framelog.ui.onboarding

/**
 * Enumeration of every onboarding step in the DETENT first-run coach mark flow.
 *
 * Steps with [showsOverlay] = false are handled by dedicated full-screen composables
 * (WelcomeScreen, WidgetSetupScreen) rather than the floating coach card overlay.
 *
 * Spotlight coordinates are normalized fractions of the screen dimensions:
 *   - [spotlightCenterXFraction]: 0.0 = left edge, 1.0 = right edge
 *   - [spotlightCenterYFraction]: 0.0 = top edge, 1.0 = bottom edge
 *   - [spotlightWidthFraction]: fraction of screen width for the oval's width
 *   - [spotlightHeightFraction]: fraction of screen height for the oval's height
 */
enum class OnboardingStep(
    val stepNumber: Int,
    val title: String,
    val body: String,
    val spotlightCenterXFraction: Float,
    val spotlightCenterYFraction: Float,
    val spotlightWidthFraction: Float,
    val spotlightHeightFraction: Float,
    val showsOverlay: Boolean,
) {

    /**
     * Step 1 — Full-screen welcome composable (WelcomeScreen).
     * No overlay rendered; WelcomeScreen handles all UI for this step.
     */
    WELCOME(
        stepNumber = 1,
        title = "",
        body = "",
        spotlightCenterXFraction = 0.5f,
        spotlightCenterYFraction = 0.5f,
        spotlightWidthFraction = 0f,
        spotlightHeightFraction = 0f,
        showsOverlay = false,
    ),

    /**
     * Step 2 — Gear Library, Lenses tab.
     * Spotlights the FAB in the bottom-right corner.
     */
    ADD_LENS(
        stepNumber = 2,
        title = "Start with your glass",
        body = "Your gear library starts with lenses — tap + Add Lens to add your first one. " +
            "Lenses define your mount type, which tells the app which camera bodies are compatible.",
        spotlightCenterXFraction = 0.87f,
        spotlightCenterYFraction = 0.84f,
        spotlightWidthFraction = 0.20f,
        spotlightHeightFraction = 0.10f,
        showsOverlay = true,
    ),

    /**
     * Step 3 — Gear Library, Bodies tab.
     * Spotlights the FAB in the bottom-right corner.
     */
    ADD_BODY(
        stepNumber = 3,
        title = "Add a camera body",
        body = "Select your mount type from the list — it's already populated from your lens. " +
            "Shutter increments control the shutter speed stepper when this body is active.",
        spotlightCenterXFraction = 0.87f,
        spotlightCenterYFraction = 0.84f,
        spotlightWidthFraction = 0.20f,
        spotlightHeightFraction = 0.10f,
        showsOverlay = true,
    ),

    /**
     * Step 4 — Gear Library, Filters tab.
     * Spotlights the tab row to draw attention to navigating between tabs.
     */
    FILTERS_TOUR(
        stepNumber = 4,
        title = "Filters are optional",
        body = "Add filters to your library if you use them. Filter size (mm) matches your lens " +
            "thread — leave it blank and the filter will show as compatible with all lenses.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.12f,
        spotlightWidthFraction = 0.90f,
        spotlightHeightFraction = 0.06f,
        showsOverlay = true,
    ),

    /**
     * Step 5 — Gear Library, Film Stocks tab.
     * Spotlights the FAB.
     */
    ADD_FILM_STOCK(
        stepNumber = 5,
        title = "Add a film stock",
        body = "Enter the box speed ISO — push and pull adjustments happen at the roll level, " +
            "not here. 'Discontinued' hides the stock from roll setup without deleting it.",
        spotlightCenterXFraction = 0.87f,
        spotlightCenterYFraction = 0.84f,
        spotlightWidthFraction = 0.20f,
        spotlightHeightFraction = 0.10f,
        showsOverlay = true,
    ),

    /**
     * Step 6 — Gear Library, Kits tab.
     * Spotlights the FAB.
     */
    KITS_TOUR(
        stepNumber = 6,
        title = "Kits are gear presets",
        body = "A kit bundles a camera body, lenses, and filters into a named preset. At roll " +
            "setup, loading a kit pre-fills all your gear in one tap.",
        spotlightCenterXFraction = 0.87f,
        spotlightCenterYFraction = 0.84f,
        spotlightWidthFraction = 0.20f,
        spotlightHeightFraction = 0.10f,
        showsOverlay = true,
    ),

    /**
     * Step 7 — Roll List screen.
     * Spotlights the FAB so the user knows to create their first roll.
     */
    CREATE_ROLL(
        stepNumber = 7,
        title = "Load your first roll",
        body = "Tap + to set up a new roll. Select your film stock, camera body, and lenses. " +
            "Make sure to tap \u2018Create & Load\u2019 \u2014 not just \u2018Create Roll\u2019 " +
            "\u2014 to load the film and start logging.",
        spotlightCenterXFraction = 0.87f,
        spotlightCenterYFraction = 0.84f,
        spotlightWidthFraction = 0.20f,
        spotlightHeightFraction = 0.10f,
        showsOverlay = true,
    ),

    /**
     * Step 8 — Roll Journal screen.
     * Spotlights the frame card area at the top of the list.
     */
    ROLL_JOURNAL_TOUR(
        stepNumber = 8,
        title = "Your roll journal",
        body = "Every frame slot is created in advance. Frame 1 is your current frame. " +
            "Tap any frame to edit it — useful for retroactive corrections.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.28f,
        spotlightWidthFraction = 0.85f,
        spotlightHeightFraction = 0.12f,
        showsOverlay = true,
    ),

    /**
     * Step 9a — Quick Screen: header / active roll indicator.
     */
    QS_HEADER(
        stepNumber = 9,
        title = "Your active roll",
        body = "Tap the roll name to switch between loaded rolls if you're shooting " +
            "multiple cameras at once.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.44f,
        spotlightWidthFraction = 0.85f,
        spotlightHeightFraction = 0.08f,
        showsOverlay = true,
    ),

    /**
     * Step 9b — Quick Screen: lens cycle row.
     */
    QS_LENS(
        stepNumber = 9,
        title = "Cycle your lenses",
        body = "Tap to cycle through the lenses configured on this roll.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.52f,
        spotlightWidthFraction = 0.85f,
        spotlightHeightFraction = 0.08f,
        showsOverlay = true,
    ),

    /**
     * Step 9c — Quick Screen: filter chips row.
     */
    QS_FILTERS(
        stepNumber = 9,
        title = "Toggle filters",
        body = "Tap a chip to toggle a filter on or off. The EV sum updates automatically. " +
            "Tap + to access all available filters.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.60f,
        spotlightWidthFraction = 0.85f,
        spotlightHeightFraction = 0.09f,
        showsOverlay = true,
    ),

    /**
     * Step 9d — Quick Screen: aperture and shutter speed steppers.
     */
    QS_STEPPERS(
        stepNumber = 9,
        title = "Aperture and shutter speed",
        body = "Set your exposure values here. Shutter speeds at 1 second or slower are " +
            "highlighted — watch for motion blur.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.73f,
        spotlightWidthFraction = 0.85f,
        spotlightHeightFraction = 0.17f,
        showsOverlay = true,
    ),

    /**
     * Step 9e — Quick Screen: frame pointer indicator.
     */
    QS_FRAME(
        stepNumber = 9,
        title = "Frame pointer",
        body = "Shows your current frame. If you've logged frames out of order, tap the " +
            "frontier indicator to jump back to the current frame.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.44f,
        spotlightWidthFraction = 0.60f,
        spotlightHeightFraction = 0.07f,
        showsOverlay = true,
    ),

    /**
     * Step 9f — Quick Screen: log frame button.
     */
    QS_LOG_FRAME(
        stepNumber = 9,
        title = "Log the frame",
        body = "Tap to log the current frame. The next frame inherits all the same settings " +
            "— change only what changed between shots. That's the whole workflow.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.88f,
        spotlightWidthFraction = 0.85f,
        spotlightHeightFraction = 0.09f,
        showsOverlay = true,
    ),

    /**
     * Step 10 — Roll List, Finished/Archived tabs.
     * Spotlights the tab row.
     */
    FINISHED_ROLLS_TOUR(
        stepNumber = 10,
        title = "Finished and archived rolls",
        body = "When you finish a roll, it moves here. Tap any roll to open its journal, " +
            "edit frames, and export your data. Archive rolls you're done with to keep things tidy.",
        spotlightCenterXFraction = 0.50f,
        spotlightCenterYFraction = 0.12f,
        spotlightWidthFraction = 0.90f,
        spotlightHeightFraction = 0.06f,
        showsOverlay = true,
    ),

    /**
     * Step 11 — Widget setup instructions screen (WidgetSetupScreen).
     * No overlay; WidgetSetupScreen handles all UI for this step.
     */
    WIDGET_SETUP_SCREEN(
        stepNumber = 11,
        title = "",
        body = "",
        spotlightCenterXFraction = 0.5f,
        spotlightCenterYFraction = 0.5f,
        spotlightWidthFraction = 0f,
        spotlightHeightFraction = 0f,
        showsOverlay = false,
    ),

    /**
     * Terminal state — onboarding is complete.
     * No overlay or dedicated screen; the NavGraph navigates to QuickScreen.
     */
    COMPLETE(
        stepNumber = 11,
        title = "",
        body = "",
        spotlightCenterXFraction = 0.5f,
        spotlightCenterYFraction = 0.5f,
        spotlightWidthFraction = 0f,
        spotlightHeightFraction = 0f,
        showsOverlay = false,
    ),
}

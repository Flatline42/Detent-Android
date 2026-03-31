package com.southsouthwest.framelog.ui.theme

import androidx.compose.ui.graphics.Color

// High Noon (Alpine)
val AlpineBackground = Color(0xFFFAF9F6)
val SafetyGold = Color(0xFFFF8F00)

// Golden Hour (Street)
val GoldenHourBackground = Color(0xFF121212)
val BurntOrange = Color(0xFFF7882F)
val WarmCream = Color(0xFFFFCC80)

// ---------------------------------------------------------------------------
// Scroll wheel tier colors
// ---------------------------------------------------------------------------

// Selected center value — themed to each palette
val GoldenHourAmber = Color(0xFFFFBF00)   // Golden Hour selected value
val AlpineGold = Color(0xFFFFD700)         // Alpine selected value

// Adjacent (±1 step) — muted secondary color per palette
val DustyMauve = Color(0xFFA28089)         // Golden Hour adjacent
val WheelAdjLightGray = Color(0xFFD1D1D1)  // Alpine adjacent

// Far peek (±2+ steps) and dots/pipes — 50% opacity of the adjacent colors (computed in composable)

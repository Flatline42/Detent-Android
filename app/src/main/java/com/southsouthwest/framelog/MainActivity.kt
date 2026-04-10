package com.southsouthwest.framelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.AppTheme
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.ui.navigation.FrameLogNavGraph
import com.southsouthwest.framelog.ui.onboarding.OnboardingViewModel
import com.southsouthwest.framelog.ui.theme.DetentTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appPrefs = AppPreferences(this)

        setContent {
            val appTheme by appPrefs.appThemeFlow.collectAsState(initial = appPrefs.appTheme)
            val isDarkTheme = when (appTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            DetentTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                // OnboardingViewModel lives at the activity level so it persists across
                // all destination changes during the coached first-run flow.
                val onboardingViewModel: OnboardingViewModel = viewModel()
                FrameLogNavGraph(navController, onboardingViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pre-warm the Room connection so it's open by the time the user taps Log Frame.
        // The first Room query after a cold start (or after the phone has been idle with
        // the connection closed) can add 1-2 seconds of latency. This lightweight count
        // query opens the connection in the background during app resume, before any
        // interaction is possible.
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(this@MainActivity).rollDao().getRollCount()
        }
    }
}

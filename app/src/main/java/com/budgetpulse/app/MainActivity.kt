package com.budgetpulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.budgetpulse.app.data.prefs.ThemeMode
import com.budgetpulse.app.data.prefs.UserPrefs
import com.budgetpulse.app.ui.common.LocalAppContainer
import com.budgetpulse.app.ui.common.LocalPerfTier
import com.budgetpulse.app.ui.common.Performance
import com.budgetpulse.app.ui.navigation.BudgetPulseRoot
import com.budgetpulse.app.ui.splash.SplashScreen
import com.budgetpulse.app.ui.theme.BudgetPulseTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BudgetPulseApp).container
        setContent {
            // WindowSizeClass is recalculated on rotation / window resize / fold, so layouts adapt live.
            val windowSize = calculateWindowSizeClass(this)
            val prefs by container.userPreferences.prefs.collectAsStateWithLifecycle<UserPrefs?>(initialValue = null)
            val current = prefs
            val tier = remember { Performance.tierFor(this) }
            // Show the animated splash at least ~1.5 s, and for as long as the saved settings are still loading.
            var minTimeElapsed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(1500); minTimeElapsed = true }
            CompositionLocalProvider(LocalAppContainer provides container, LocalPerfTier provides tier) {
                BudgetPulseTheme(themeMode = current?.themeMode ?: ThemeMode.SYSTEM) {
                    Crossfade(targetState = current == null || !minTimeElapsed, animationSpec = tween(350), label = "splash") { showSplash ->
                        if (showSplash || current == null) {
                            SplashScreen()
                        } else {
                            BudgetPulseRoot(windowSize = windowSize, prefs = current)
                        }
                    }
                }
            }
        }
    }
}

package com.budgetpulse.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewAnimationUtils
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
import kotlin.math.hypot
import com.budgetpulse.app.data.prefs.ThemeMode
import com.budgetpulse.app.data.prefs.UserPrefs
import com.budgetpulse.app.ui.common.LocalAppContainer
import com.budgetpulse.app.ui.common.LocalPerfTier
import com.budgetpulse.app.ui.common.Performance
import com.budgetpulse.app.ui.navigation.BudgetPulseRoot
import com.budgetpulse.app.ui.splash.SplashScreen
import com.budgetpulse.app.ui.theme.BudgetPulseTheme

private class SplashEchoDrawable(width: Int, height: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val centerX = width / 2f
    private val centerY = height / 2f
    private val maxRadius = hypot(centerX, centerY)
    var progress: Float = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    init {
        setBounds(0, 0, width, height)
    }

    override fun draw(canvas: Canvas) {
        repeat(3) { index ->
            val phase = (progress + index * 0.29f) % 1f
            paint.color = Color.argb(((1f - phase) * 115f).toInt(), 255, 112, 32)
            canvas.drawCircle(centerX, centerY, maxRadius * phase, paint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashView ->
                val centerX = splashView.width / 2
                val centerY = splashView.height / 2
                val endRadius = hypot(centerX.toFloat(), centerY.toFloat())
                val reveal = ViewAnimationUtils.createCircularReveal(
                    splashView, centerX, centerY, endRadius, 0f
                ).apply { duration = 850L }
                val echoes = SplashEchoDrawable(splashView.width, splashView.height)
                splashView.overlay.add(echoes)
                val echoAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 850L
                    addUpdateListener {
                        echoes.progress = it.animatedValue as Float
                        splashView.invalidate()
                    }
                }
                val zoom = ObjectAnimator.ofFloat(splashView, View.SCALE_X, 1f, 1.08f).apply {
                    duration = 850L
                }
                val zoomY = ObjectAnimator.ofFloat(splashView, View.SCALE_Y, 1f, 1.08f).apply {
                    duration = 850L
                }
                AnimatorSet().apply {
                    playTogether(reveal, zoom, zoomY, echoAnimator)
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            splashView.overlay.remove(echoes)
                            splashView.remove()
                        }
                    })
                    start()
                }
            }
        }
        enableEdgeToEdge()
        val container = (application as BudgetPulseApp).container
        setContent {
            // WindowSizeClass is recalculated on rotation / window resize / fold, so layouts adapt live.
            val windowSize = calculateWindowSizeClass(this)
            val prefs by container.userPreferences.prefs.collectAsStateWithLifecycle<UserPrefs?>(initialValue = null)
            val current = prefs
            val tier = remember { Performance.tierFor(this) }
            // Keep the splash visible for about 5 seconds, and longer if saved settings are still loading.
            var minTimeElapsed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(4650); minTimeElapsed = true }
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

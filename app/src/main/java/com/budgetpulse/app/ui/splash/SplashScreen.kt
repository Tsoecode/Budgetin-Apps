package com.budgetpulse.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetpulse.app.R
import com.budgetpulse.app.ui.common.AppIcon

// The three app colours the gradient drifts between. White on all of them is >= 3:1 (large logo / text).
private val DEEP = Color(0xFF9A3A07)
private val BRAND = Color(0xFFC44F0C)
private val BRIGHT = Color(0xFFEC6D17)

/**
 * Start-up screen: a slowly drifting gradient in the app palette with the logo in the centre.
 * The animated value is only read while drawing, so the screen does not recompose every frame.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "splashDrift").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(700)) }

    Box(
        modifier.fillMaxSize().testTag("splash").drawBehind {
            val t = drift.value
            val brush = Brush.linearGradient(
                colors = listOf(lerp(DEEP, BRAND, t), lerp(BRAND, BRIGHT, t), lerp(BRIGHT, DEEP, t)),
                start = Offset(size.width * t, 0f),
                end = Offset(size.width * (1f - t), size.height)
            )
            drawRect(brush)
        },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.graphicsLayer {
                alpha = enter.value
                val s = 0.85f + 0.15f * enter.value
                scaleX = s; scaleY = s
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(132.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                AppIcon(R.drawable.ic_logo_mark, contentDescription = "Budgetin logo", tint = Color.White, modifier = Modifier.size(84.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Budgetin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

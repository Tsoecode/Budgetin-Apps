package com.budgetpulse.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetpulse.app.R
import com.budgetpulse.app.ui.common.AppIcon
import kotlin.math.PI
import kotlin.math.sin

// Saturated orange/coral fields stand out against a clean, bright white base.
private val LIGHT_ORANGE = Color(0xFFFF8A00)
private val PALE_ORANGE = Color(0xFFFFB15C)
private val BRAND_ORANGE = Color(0xFFFF4F32)
private val AMBER_GLOW = Color(0xFFFF6A00)
private val WARM_WHITE = Color(0xFFFFFFFF)

/**
 * Start-up screen: an animated, flowing gradient in the app palette with the logo in the centre.
 * The animated value is only read while drawing, so the screen does not recompose every frame.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "splashDrift").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "drift"
    )
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(700)) }

    Box(
        modifier.fillMaxSize().testTag("splash").drawBehind {
            val phase = drift.value * (2f * PI.toFloat())
            val width = size.width
            val height = size.height
            val radius = size.maxDimension * 0.68f

            // Vivid color centers with a shorter fade keep the orange fields distinct,
            // while the untouched areas stay crisp white.
            drawRect(WARM_WHITE)
            drawRect(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to AMBER_GLOW.copy(alpha = 0.98f),
                        0.32f to LIGHT_ORANGE.copy(alpha = 0.88f),
                        0.72f to PALE_ORANGE.copy(alpha = 0.40f),
                        1f to Color.Transparent
                    ),
                    center = Offset(
                        width * (0.04f + 0.08f * sin(phase)),
                        height * (0.48f + 0.08f * sin(phase + 1.1f)),
                    ),
                    radius = radius
                )
            )
            drawRect(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to BRAND_ORANGE.copy(alpha = 0.96f),
                        0.34f to BRAND_ORANGE.copy(alpha = 0.78f),
                        0.72f to PALE_ORANGE.copy(alpha = 0.38f),
                        1f to Color.Transparent
                    ),
                    center = Offset(
                        width * (0.94f - 0.08f * sin(phase + 0.8f)),
                        height * (0.48f + (0.08f * sin(phase + 2.0f)))
                    ),
                    radius = radius * 0.64f
                )
            )
            drawRect(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to LIGHT_ORANGE.copy(alpha = 0.72f),
                        0.38f to PALE_ORANGE.copy(alpha = 0.46f),
                        1f to Color.Transparent
                    ),
                    center = Offset(
                        width * (0.5f + 0.16f * sin(phase + 2.4f)),
                        height * (0.5f + 0.10f * sin(phase + 3.1f))
                    ),
                    radius = radius * 0.68f
                )
            )
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
            Box(
                Modifier.size(132.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(Color(0xFFFF6A00), Color(0xFFCC3500))),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(R.drawable.ic_logo_mark, contentDescription = "BudgetPulse logo", tint = Color.White, modifier = Modifier.size(84.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("BudgetPulse", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFCC3500))
        }
    }
}

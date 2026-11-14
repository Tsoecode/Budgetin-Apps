package com.budgetpulse.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.budgetpulse.app.R
import com.budgetpulse.app.ui.common.AppIcon
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// Saturated orange/coral fields stand out against a clean, bright white base.
private val LIGHT_ORANGE = Color(0xFFFF8A00)
private val PALE_ORANGE = Color(0xFFFFB15C)
private val BRAND_ORANGE = Color(0xFFFF4F32)
private val AMBER_GLOW = Color(0xFFFF6A00)
private val WARM_WHITE = Color(0xFFFFFFFF)

private data class SplashBlob(
    val seedX: Float,
    val seedY: Float,
    val phase: Float,
    val speed: Float,
    val sizeScale: Float,
    val color: Color
)

/** Animated splash with a compact color field, dominant white space and glass-style logo. */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val drift = rememberInfiniteTransition(label = "splashDrift").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "drift"
    )
    val blobs = remember {
        List(5) { index ->
            SplashBlob(
                seedX = Random.nextFloat(),
                seedY = Random.nextFloat(),
                phase = Random.nextFloat() * 2f * PI.toFloat(),
                speed = 0.65f + Random.nextFloat() * 0.7f,
                sizeScale = listOf(0.62f, 1.28f, 0.82f, 1.12f, 0.72f)[index],
                color = listOf(
                    AMBER_GLOW,
                    BRAND_ORANGE,
                    LIGHT_ORANGE,
                    Color(0xFFFF3E68),
                    Color(0xFFFFC02E)
                )[index]
            )
        }
    }
    val logoFlow = rememberInfiniteTransition(label = "logoGradient").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "logoFlow"
    )
    val phaseOffset = remember { Random.nextFloat() * (2f * PI.toFloat()) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(700)) }

    Box(
        modifier.fillMaxSize().testTag("splash").drawBehind {
            val time = drift.value * 2f * PI.toFloat() + phaseOffset
            val width = size.width
            val height = size.height
            // Five independently sized blobs keep the color light and varied across the screen.
            drawRect(WARM_WHITE)
            blobs.forEach { blob ->
                val radius = size.minDimension * 0.17f * blob.sizeScale
                val phase = time * blob.speed + blob.phase
                val center = Offset(
                    (width * (blob.seedX + 0.22f * sin(phase))).coerceIn(0f, width),
                    (height * (blob.seedY + 0.22f * sin(phase * 0.71f + blob.phase))).coerceIn(0f, height)
                )
                val visibility = ((sin(phase * 0.55f + blob.phase) + 1f) / 2f)
                    .coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to blob.color.copy(alpha = 0.92f * visibility),
                            0.42f to LIGHT_ORANGE.copy(alpha = 0.70f * visibility),
                            0.78f to PALE_ORANGE.copy(alpha = 0.30f * visibility),
                            1f to Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    alpha = enter.value
                    val scale = 0.82f + 0.18f * enter.value
                    scaleX = scale
                    scaleY = scale
                }
                .size(148.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.72f), Color(0xFFFFF1E8).copy(alpha = 0.54f))
                    ),
                    CircleShape
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.92f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                R.drawable.ic_logo_mark,
                contentDescription = "BudgetPulse logo",
                tint = Color.White,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val shift = logoFlow.value
                        val gradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFB000),
                                Color(0xFFFF5A00),
                                Color(0xFFE83D45),
                                Color(0xFFFFB000)
                            ),
                            start = Offset(size.width * (shift - 1f), size.height),
                            end = Offset(size.width * shift, 0f)
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(gradient, blendMode = BlendMode.SrcIn)
                        }
                    }
            )
        }
    }
}

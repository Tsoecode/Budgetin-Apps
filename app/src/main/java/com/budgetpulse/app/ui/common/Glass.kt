package com.budgetpulse.app.ui.common

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.budgetpulse.app.R
import com.budgetpulse.app.ui.navigation.TopLevelRoute
import com.budgetpulse.app.ui.theme.BrandPeach

/**
 * Glassmorphism, implemented natively (no extra library):
 *
 *  1. The page (background + screen content) is recorded once into a [GraphicsLayer] ([glassSource]).
 *  2. A [GlassPanel] draws that layer again *behind itself*, shifted so it lines up with the page,
 *     with a RenderEffect blur applied (Android 12+ / API 31+), then adds a translucent white tint,
 *     a light rim and a soft shadow.
 *
 * Below API 31 there is no RenderEffect, so the panel falls back to a more opaque tint and stays readable.
 */
@Stable
class GlassSource(val layer: GraphicsLayer) {
    /** Position of the recorded page in the window; the panels subtract it to align the blurred copy. */
    var rootOffset by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassSource(): GlassSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassSource(layer) }
}

/** Marks everything drawn by the inner modifiers and children as the "page" that glass panels blur. */
fun Modifier.glassSource(source: GlassSource): Modifier =
    this
        .onGloballyPositioned { source.rootOffset = it.positionInRoot() }
        .drawWithContent {
            source.layer.record { this@drawWithContent.drawContent() }
            drawLayer(source.layer)
        }

/**
 * Page background. Light: the Budgetin peach header gradient. Dark: graphite with two soft colour glows
 * (orange top-right, indigo bottom-left) instead of just darkening the light design. Draw it INSIDE [glassSource].
 */
@Composable
fun Modifier.appBackground(): Modifier {
    val base = MaterialTheme.colorScheme.background
    if (isDark()) {
        return this.background(base).drawBehind {
            val reach = maxOf(size.width, size.height)
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFFF7A2F).copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.02f), radius = reach * 0.75f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF5B6CFF).copy(alpha = 0.17f), Color.Transparent),
                    center = Offset(size.width * 0.02f, size.height * 0.98f), radius = reach * 0.70f
                )
            )
        }
    }
    val top = Color(0xFFFFC9A3).copy(alpha = 0.95f)
    val mid = BrandPeach.copy(alpha = 0.35f)
    return this
        .background(base)
        .background(Brush.verticalGradient(0f to top, 0.22f to mid, 0.5f to base.copy(alpha = 0f), 1f to base.copy(alpha = 0f)))
}

@Composable
fun GlassPanel(
    source: GlassSource?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isDark()
    val blurLayer = rememberGraphicsLayer()
    var panelRoot by remember { mutableStateOf(Offset.Zero) }
    // source == null on Lite devices: no blur layer at all, only the (more opaque) frosted tint.
    val canBlur = source != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Without a real blur the tint has to do more work to keep text readable.
    val a1 = if (canBlur) 0.62f else 0.88f
    val a2 = if (canBlur) 0.40f else 0.80f
    val tint = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF222936).copy(alpha = a1), Color(0xFF12151B).copy(alpha = a2)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = a1 + 0.08f), Color.White.copy(alpha = a2)))
    }
    val rim = if (dark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.30f)))
    }
    Box(
        modifier
            .shadow(14.dp, shape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x44B4501A))
            .clip(shape)
            .onGloballyPositioned { panelRoot = it.positionInRoot() }
            .drawBehind {
                if (canBlur && source != null) {
                    val shift = panelRoot - source.rootOffset
                    blurLayer.renderEffect = BlurEffect(38f, 38f, TileMode.Clamp)
                    blurLayer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
                        translate(left = -shift.x, top = -shift.y) { drawLayer(source.layer) }
                    }
                    drawLayer(blurLayer)
                }
            }
            .background(tint)
            .border(1.dp, rim, shape),
        content = content
    )
}

// --------------------------------------------------------------------------- navigation widgets

/** One destination: icon above label (bar / rail) or icon beside label (drawer). */
@Composable
private fun GlassNavItem(
    route: TopLevelRoute<out Any>,
    selected: Boolean,
    onClick: () -> Unit,
    horizontal: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val pill by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "navPill")
    val tint = if (selected) primary else idle
    val shape = RoundedCornerShape(22.dp)
    val itemModifier = modifier
        .clip(shape)
        .background(primary.copy(alpha = 0.16f * pill), shape)
        .selectable(selected = selected, role = Role.Tab, onClick = onClick)
    if (horizontal) {
        Row(
            itemModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(route.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                route.label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    } else {
        Column(
            itemModifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AppIcon(route.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Text(
                route.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Clip,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/** Floating glass pill at the bottom of phone screens (Compact width). */
@Composable
fun GlassBottomBar(
    source: GlassSource?,
    items: List<TopLevelRoute<out Any>>,
    isSelected: (TopLevelRoute<out Any>) -> Boolean,
    onSelect: (TopLevelRoute<out Any>) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(source, modifier, RoundedCornerShape(32.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { item ->
                GlassNavItem(
                    item, isSelected(item), { onSelect(item) }, horizontal = false,
                    modifier = Modifier.weight(1f).testTag("nav_" + item.label)
                )
            }
        }
    }
}

/**
 * Floating glass panel for tablets: icon-only rail (Medium) or labelled drawer ([expanded], Expanded width).
 * Scrolls when the window is short (e.g. a phone in landscape).
 */
@Composable
fun GlassSideNav(
    source: GlassSource?,
    items: List<TopLevelRoute<out Any>>,
    isSelected: (TopLevelRoute<out Any>) -> Boolean,
    onSelect: (TopLevelRoute<out Any>) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    GlassPanel(source, modifier, RoundedCornerShape(32.dp)) {
        Column(
            Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BrandMark(showName = expanded)
            Spacer(Modifier.height(6.dp))
            items.forEach { item ->
                GlassNavItem(
                    item, isSelected(item), { onSelect(item) }, horizontal = expanded,
                    modifier = Modifier.testTag("nav_" + item.label)
                )
            }
        }
    }
}

/** The Budgetin logo: a white "b" (its bowl is a coin ring) on a rounded orange gradient tile. */
@Composable
fun BudgetinLogo(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Color(0xFFEC6D17), Color(0xFFC44F0C)))),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(R.drawable.ic_logo_mark, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.74f))
    }
}

/** Logo (+ app name when there is room). */
@Composable
fun BrandMark(showName: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BudgetinLogo()
        if (showName) {
            Spacer(Modifier.width(10.dp))
            Text("Budgetin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

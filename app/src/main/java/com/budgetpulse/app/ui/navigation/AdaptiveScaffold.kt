package com.budgetpulse.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgetpulse.app.ui.common.GlassBottomBar
import com.budgetpulse.app.ui.common.GlassSideNav
import com.budgetpulse.app.ui.common.GlassSource
import com.budgetpulse.app.ui.common.LocalBottomInset
import com.budgetpulse.app.ui.common.LocalPerfTier
import com.budgetpulse.app.ui.common.PerfTier
import com.budgetpulse.app.ui.common.appBackground
import com.budgetpulse.app.ui.common.glassSource
import com.budgetpulse.app.ui.common.rememberGlassSource

private val RAIL_WIDTH: Dp = 88.dp
private val DRAWER_WIDTH: Dp = 244.dp
private val PANEL_MARGIN: Dp = 12.dp

/**
 * Chooses the navigation chrome from the window width class. All three variants are floating
 * "glass" panels that blur the page behind them:
 *
 *   Compact  (phones)                    -> pill-shaped bottom bar, 5 destinations; the page scrolls UNDER it
 *   Medium   (tablet portrait, foldable) -> floating rail on the left, all 7 destinations
 *   Expanded (tablet landscape, desktop) -> floating labelled drawer on the left
 */
@Composable
fun AdaptiveScaffold(
    widthClass: WindowWidthSizeClass,
    showNavigation: Boolean,
    isSelected: (TopLevelRoute<out Any>) -> Boolean,
    onSelect: (TopLevelRoute<out Any>) -> Unit,
    content: @Composable () -> Unit
) {
    val source = rememberGlassSource()
    // Lite devices skip the page capture and the blur completely.
    val blurOn = LocalPerfTier.current == PerfTier.Full
    val glass: GlassSource? = if (blurOn) source else null
    val density = LocalDensity.current
    val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barHeight by remember { mutableStateOf(0.dp) }
    val compact = widthClass == WindowWidthSizeClass.Compact
    val expanded = widthClass == WindowWidthSizeClass.Expanded
    val sideWidth = if (expanded) DRAWER_WIDTH else RAIL_WIDTH

    Box(Modifier.fillMaxSize()) {
        // The "page": gradient + current screen. Glass panels draw a blurred copy of this layer.
        Box(Modifier.fillMaxSize().then(if (blurOn) Modifier.glassSource(source) else Modifier).appBackground()) {
            when {
                !showNavigation -> Box(Modifier.fillMaxSize().navigationBarsPadding()) { content() }
                compact -> {
                    // Lists add this much bottom padding, so the last item can be scrolled above the bar.
                    val inset = barHeight + PANEL_MARGIN * 2 + systemNavInset
                    CompositionLocalProvider(LocalBottomInset provides inset) {
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
                else -> Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.width(sideWidth + PANEL_MARGIN * 2))
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { content() }
                }
            }
        }

        if (showNavigation) {
            if (compact) {
                GlassBottomBar(
                    source = glass,
                    items = topLevelRoutes.filter { it.inBottomBar },
                    isSelected = isSelected,
                    onSelect = onSelect,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = PANEL_MARGIN)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } }
                        .testTag("bottom_nav")
                )
            } else {
                GlassSideNav(
                    source = glass,
                    items = topLevelRoutes,
                    isSelected = isSelected,
                    onSelect = onSelect,
                    expanded = expanded,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(PANEL_MARGIN)
                        .width(sideWidth)
                        .fillMaxHeight()
                        .testTag(if (expanded) "nav_drawer" else "nav_rail")
                )
            }
        }
    }
}

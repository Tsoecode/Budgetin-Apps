package com.budgetpulse.app.ui.common

import com.budgetpulse.app.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.budgetpulse.app.domain.model.NewsItem
import com.budgetpulse.app.ui.theme.BrandOrange
import kotlinx.coroutines.delay

/**
 * Swipeable, clickable news cards (next card peeks in from the side) with page dots.
 * Set [autoScroll] = false in UI tests so the idle-wait is not blocked by the timer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsCarousel(
    items: List<NewsItem>,
    onOpen: (NewsItem) -> Unit,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    // Auto-advance costs a wake-up every 5 s: only on capable phones.
    if (autoScroll && LocalPerfTier.current == PerfTier.Full) {
        LaunchedEffect(items.size) {
            while (true) {
                delay(5000)
                if (!pagerState.isScrollInProgress) pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }
    Column(modifier.fillMaxWidth().testTag("news_carousel")) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            NewsCard(items[page], onClick = { onOpen(items[page]) })
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            repeat(items.size) { i ->
                val active = pagerState.currentPage == i
                Box(
                    Modifier.padding(3.dp).size(if (active) 8.dp else 6.dp).clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "News: ${item.title}, from ${item.source}. Double tap to read." },
        onClick = onClick
    ) {
        Box(Modifier.fillMaxWidth().height(176.dp)) {
            SubcomposeAsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { BannerFallback(loading = true) },
                error = { BannerFallback(loading = false) }
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(item.source, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFD9BF), maxLines = 1)
                Text(
                    item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Shown while the banner downloads or when the device is offline, so the card never looks broken. */
@Composable
private fun BannerFallback(loading: Boolean) {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A1D0B), BrandOrange))),
        contentAlignment = Alignment.Center
    ) {
        if (!loading) AppIcon(R.drawable.ic_show_chart, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
    }
}

package com.budgetpulse.app.ui.tips

import com.budgetpulse.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpulse.app.ui.common.*

@Composable
fun TipsScreen(widthClass: WindowWidthSizeClass) {
    val container = LocalAppContainer.current
    val vm = appViewModel { TipsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val wide = widthClass != WindowWidthSizeClass.Compact
    // The embedded article needs a WebView (~100 MB): on Lite phones it only loads when the user taps Show.
    val perfTier = LocalPerfTier.current
    var showGuide by rememberSaveable { mutableStateOf(perfTier == PerfTier.Full) }

    val tipsItems: LazyListScope.() -> Unit = {
        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text("Small habits, big difference", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Nine practical ideas you can start today, plus an embedded beginner's guide from Bank Jago.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        SAVINGS_TIPS.forEachIndexed { index, tip ->
            item(key = "tip$index") {
                AppCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        AppIcon(tip.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text((index + 1).toString() + ". " + tip.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    val rightItems: LazyListScope.() -> Unit = {
        item {
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Embedded guide", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showGuide = !showGuide }) { Text(if (showGuide) "Hide" else "Show") }
                    }
                    Text("Saving tips for beginners - Bank Jago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (showGuide) {
                        EmbeddedWebPage(EMBEDDED_TIPS_URL, Modifier.fillMaxWidth().height(520.dp).testTag("embedded_tips"))
                    }
                    TextButton(onClick = { openUrl(context, TIPS_SHARE_LINK) }) {
                        AppIcon(R.drawable.ic_open_in_browser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Open in browser")
                    }
                }
            }
        }
        item {
            Column {
                SectionHeader("Finance news this week", actionLabel = if (state.refreshing) null else "Refresh", onAction = { vm.refresh(true) })
                if (state.refreshing && state.news.isEmpty()) LoadingBox()
                if (state.news.isEmpty() && !state.refreshing) EmptyHint(state.error ?: "No news saved yet. Connect to the internet and tap Refresh.")
                NewsCarousel(state.news, onOpen = { openUrl(context, it.link) })
                state.error?.let { if (state.news.isNotEmpty()) InlineError(it) }
                Text(
                    "News via Google News. Stories change every week.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    ScreenScaffold(title = "Savings tips & news") {
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(10.dp)) { tipsItems() }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(10.dp)) { rightItems() }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) { rightItems(); tipsItems() }
        }
    }
}

package com.budgetpulse.app.ui.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.R
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.model.AppNotification
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.ui.common.AppCard
import com.budgetpulse.app.ui.common.AppIcon
import com.budgetpulse.app.ui.common.CircleIconButton
import com.budgetpulse.app.ui.common.LoadingBox
import com.budgetpulse.app.ui.common.LocalAppContainer
import com.budgetpulse.app.ui.common.LocalBottomInset
import com.budgetpulse.app.ui.common.ScreenScaffold
import com.budgetpulse.app.ui.common.appViewModel
import com.budgetpulse.app.util.formatMoney
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val loading: Boolean = true,
    val items: List<AppNotification> = emptyList(),
    /** Entries that were unread when the screen was opened (shown with a dot, although they are marked read at once). */
    val newIds: Set<Long> = emptySet(),
    val currency: String = "USD"
)

class NotificationsViewModel(private val container: AppContainer) : ViewModel() {
    private val newIds = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<NotificationsUiState> = combine(
        container.notificationRepository.observeAll(), container.userPreferences.prefs, newIds
    ) { items, prefs, fresh -> NotificationsUiState(false, items, fresh, prefs.currency) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationsUiState())

    init {
        // Opening the list clears the bell badge: remember what was new, then mark everything as read.
        viewModelScope.launch {
            newIds.value = container.notificationRepository.unreadIds()
            container.notificationRepository.markAllRead()
        }
    }

    fun clearAll() {
        viewModelScope.launch { container.notificationRepository.clear() }
    }
}

@Composable
fun NotificationsScreen(onOpenTransaction: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm = appViewModel { NotificationsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    NotificationsContent(state, onOpenTransaction, onClear = vm::clearAll)
}

@Composable
fun NotificationsContent(state: NotificationsUiState, onOpen: (Long) -> Unit, onClear: () -> Unit) {
    ScreenScaffold(
        title = "Notifications",
        actions = {
            if (state.items.isNotEmpty()) {
                CircleIconButton(R.drawable.ic_delete, "Clear all notifications", onClear, Modifier.testTag("btn_clear_notifications"))
            }
        }
    ) {
        when {
            state.loading -> LoadingBox()
            state.items.isEmpty() -> EmptyNotifications()
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("notification_list"),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + LocalBottomInset.current),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.id }) { n ->
                    NotificationRow(n, state.currency, isNew = n.id in state.newIds, onClick = { n.transactionId?.let(onOpen) })
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, currency: String, isNew: Boolean, onClick: () -> Unit) {
    val income = n.type == TxType.INCOME
    val badgeColor = if (income) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val badgeTint = if (income) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val headline = if (income) "Income added" else "Expense added"
    val origin = if (n.source == TxSource.EMAIL) "Imported from e-mail" else "Added manually"
    val whenText = DateUtils.getRelativeTimeSpanString(
        n.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(badgeColor), contentAlignment = Alignment.Center) {
                AppIcon(if (income) R.drawable.ic_arrow_downward else R.drawable.ic_arrow_upward, contentDescription = null, tint = badgeTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(headline, style = MaterialTheme.typography.titleSmall, fontWeight = if (isNew) FontWeight.Bold else FontWeight.Medium)
                Text(n.title + " - " + formatMoney(n.amount, currency), style = MaterialTheme.typography.bodyMedium)
                Text(
                    n.category.label + " - " + origin + " - " + whenText,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isNew) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun EmptyNotifications() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        AppIcon(R.drawable.ic_notifications_off, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
        Spacer(Modifier.padding(6.dp))
        Text("No notifications yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "You will see a note here whenever a transaction is added by hand or imported from an e-mail.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}

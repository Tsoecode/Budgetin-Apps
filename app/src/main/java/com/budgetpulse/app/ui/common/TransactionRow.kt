package com.budgetpulse.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.util.formatMoney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

fun Transaction.formattedDate(): String = TIME_FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

fun Transaction.signedAmount(currency: String): String =
    (if (type == TxType.EXPENSE) "-" else "+") + formatMoney(amount, currency)

@Composable
fun TransactionRow(tx: Transaction, currency: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = tx.note.ifBlank { tx.category.label }
    val amountColor = if (tx.type == TxType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val a11y = (if (tx.type == TxType.EXPENSE) "Expense" else "Income") + ", " + title + ", " +
        formatMoney(tx.amount, currency) + ", " + tx.category.label + ", " + tx.formattedDate()
    Row(
        modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = a11y }
            .clickable(onClickLabel = "Open transaction", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(tx.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = tx.category.label + " - " + tx.formattedDate() + if (tx.source == TxSource.EMAIL) " - from e-mail" else ""
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(tx.signedAmount(currency), color = amountColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

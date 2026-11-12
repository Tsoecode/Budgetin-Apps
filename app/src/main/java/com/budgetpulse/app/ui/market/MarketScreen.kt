package com.budgetpulse.app.ui.market

import com.budgetpulse.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.budgetpulse.app.domain.model.CurrencyRates
import com.budgetpulse.app.ui.common.*
import com.budgetpulse.app.domain.logic.RateDisplay
import com.budgetpulse.app.ui.onboarding.SUPPORTED_CURRENCIES
import com.budgetpulse.app.util.formatRate
import com.budgetpulse.app.util.label
import java.util.Currency
import java.util.Locale

private val FLAG_CODES = mapOf(
    "USD" to "us", "EUR" to "eu", "GBP" to "gb", "IDR" to "id", "MYR" to "my", "SGD" to "sg", "JPY" to "jp", "AUD" to "au",
    "CNY" to "cn", "KRW" to "kr", "INR" to "in", "THB" to "th", "PHP" to "ph", "VND" to "vn", "HKD" to "hk", "CAD" to "ca",
    "CHF" to "ch", "NZD" to "nz", "SAR" to "sa", "AED" to "ae", "TWD" to "tw", "BRL" to "br", "ZAR" to "za", "TRY" to "tr",
    "MXN" to "mx", "SEK" to "se", "NOK" to "no", "DKK" to "dk", "PLN" to "pl", "RUB" to "ru", "UAH" to "ua", "PKR" to "pk",
    "BDT" to "bd", "LKR" to "lk", "EGP" to "eg", "ILS" to "il", "CZK" to "cz", "HUF" to "hu", "KWD" to "kw", "QAR" to "qa"
)

fun flagUrl(code: String): String? = FLAG_CODES[code]?.let { "https://flagcdn.com/w80/$it.png" }

fun currencyName(code: String): String =
    runCatching { Currency.getInstance(code).getDisplayName(Locale.ENGLISH) }.getOrDefault(code)

@Composable
fun MarketScreen(widthClass: WindowWidthSizeClass) {
    val container = LocalAppContainer.current
    val vm = appViewModel { MarketViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MarketContent(
        state = state, wide = widthClass != WindowWidthSizeClass.Compact,
        onRefresh = { vm.load(true) }, onBase = vm::setBase, onTarget = vm::setTarget,
        onAmount = vm::setAmount, onQuery = vm::setQuery,
        onOpenSource = { openUrl(context, "https://www.exchangerate-api.com") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketContent(
    state: MarketUiState, wide: Boolean, onRefresh: () -> Unit, onBase: (String) -> Unit, onTarget: (String) -> Unit,
    onAmount: (String) -> Unit, onQuery: (String) -> Unit, onOpenSource: () -> Unit
) {
    ScreenScaffold(
        title = "Currency market",
        actions = {
            IconButton(onClick = onRefresh, modifier = Modifier.testTag("btn_refresh_rates")) {
                AppIcon(R.drawable.ic_refresh, contentDescription = "Refresh exchange rates")
            }
        }
    ) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when (val r = state.rates) {
                UiState.Loading -> Box(Modifier.fillMaxSize()) { LoadingBox(Modifier.align(Alignment.Center)) }
                is UiState.Error -> LazyColumn(Modifier.fillMaxSize()) { item { ErrorBox(r.message, onRetry = onRefresh) } }
                is UiState.Success -> {
                    val data = r.data
                    val header: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatusCard(data, r.stale, onOpenSource)
                            ConverterCard(state, data, onBase, onTarget, onAmount)
                        }
                    }
                    val list: @Composable () -> Unit = { RatesListCard(state, data, onQuery) }
                    if (wide) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)) { item { header() } }
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)) { item { list() } }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("market_list"),
                            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) { item { header() }; item { list() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(data: CurrencyRates, stale: Boolean, onOpenSource: () -> Unit) {
    AppCard(containerColor = if (stale) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(if (stale) R.drawable.ic_cloud_off else R.drawable.ic_public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (stale) "Offline - showing last saved rates" else "Live market rates",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                )
            }
            Text("Updated: " + data.updatedLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Rates are daily reference rates, not tick-by-tick trading prices.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenSource, contentPadding = PaddingValues(0.dp)) { Text("Source: " + data.provider + " (tap for details)") }
        }
    }
}

@Composable
private fun ConverterCard(state: MarketUiState, data: CurrencyRates, onBase: (String) -> Unit, onTarget: (String) -> Unit, onAmount: (String) -> Unit) {
    val codes = remember(data) { orderedCodes(data) }
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Converter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.amountText, onValueChange = onAmount, label = { Text("Amount") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("converter_amount")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CurrencyPicker("From", state.base, codes, onBase, Modifier.weight(1f))
                AppIcon(R.drawable.ic_swap_horiz, contentDescription = null)
                CurrencyPicker("To", state.target, codes, onTarget, Modifier.weight(1f))
            }
            val result = state.converted
            val pair = RateDisplay.forRow(mapOf(state.base to 1.0) + (data.rates), state.base, state.target)
            Text(
                if (result == null) "Enter a valid amount" else state.amountText + " " + state.base + " = " + formatRate(result) + " " + state.target,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("converter_result").semantics { contentDescription = "Conversion result" }
            )
            if (pair != null && state.base != state.target) {
                Text(pair.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("converter_rate"))
            }
        }
    }
}

private fun orderedCodes(data: CurrencyRates): List<String> {
    val popular = SUPPORTED_CURRENCIES.filter { data.rates.containsKey(it) }
    return popular + data.rates.keys.filter { it !in popular }.sorted()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPicker(label: String, selected: String, codes: List<String>, onSelect: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            codes.forEach { code ->
                DropdownMenuItem(text = { Text(code + " - " + currencyName(code)) }, onClick = { onSelect(code); expanded = false })
            }
        }
    }
}

@Composable
private fun RatesListCard(state: MarketUiState, data: CurrencyRates, onQuery: (String) -> Unit) {
    val all = remember(data) { orderedCodes(data).filter { it != data.base } }
    val q = state.query.trim().lowercase()
    val shown = if (q.isEmpty()) all.take(25) else all.filter { it.lowercase().contains(q) || currencyName(it).lowercase().contains(q) }.take(60)
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Prices against " + data.base, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Each row shows 1 unit of the stronger currency, so the numbers stay short.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, singleLine = true, placeholder = { Text("Search currency") },
                leadingIcon = { AppIcon(R.drawable.ic_search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("rates_search")
            )
            Spacer(Modifier.height(4.dp))
            shown.forEach { code ->
                val pair = RateDisplay.forRow(data.rates, data.base, code) ?: return@forEach
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .semantics(mergeDescendants = true) { contentDescription = pair.label() + ", " + currencyName(code) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Flag(code)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(currencyName(code), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(pair.label(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("rate_$code"))
                }
            }
            if (shown.isEmpty()) EmptyHint("No currency matches your search.")
        }
    }
}

/** Country flag downloaded from flagcdn.com (Coil caches it); falls back to the currency code. */
@Composable
private fun Flag(code: String) {
    val url = flagUrl(code)
    val fallback: @Composable () -> Unit = {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(code.take(2), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
    if (url == null) {
        fallback()
    } else {
        SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(36.dp).clip(CircleShape),
            loading = { fallback() }, error = { fallback() }
        )
    }
}

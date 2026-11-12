package com.budgetpulse.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.logic.CsvExport
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayGroup(val date: LocalDate, val items: List<Transaction>, val net: Double)

data class HistoryUiState(
    val loading: Boolean = true,
    val currency: String = "USD",
    val groups: List<DayGroup> = emptyList(),
    val filter: TxType? = null,
    val query: String = "",
    val selected: Transaction? = null,
    val selectedAccountName: String = "",
    val totalCount: Int = 0,
    val csv: String = ""
)

private data class Base(val txs: List<Transaction>, val currency: String, val accounts: List<Account>)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow<TxType?>(null)
    private val query = MutableStateFlow("")
    private val selectedId = MutableStateFlow<Long?>(null)

    private val base = combine(
        container.transactionRepository.observeAll(),
        container.userPreferences.prefs,
        container.accountRepository.observeAll()
    ) { txs, prefs, accounts -> Base(txs, prefs.currency, accounts) }

    val state: StateFlow<HistoryUiState> = combine(base, filter, query, selectedId) { b, f, q, sel ->
        val zone = ZoneId.systemDefault()
        val needle = q.trim().lowercase()
        val filtered = b.txs
            .filter { f == null || it.type == f }
            .filter { needle.isEmpty() || it.note.lowercase().contains(needle) || it.category.label.lowercase().contains(needle) }
        val groups = filtered
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                DayGroup(date, list, list.sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount })
            }
            .sortedByDescending { it.date }
        val selected = b.txs.firstOrNull { it.id == sel }
        val names = b.accounts.associate { it.id to it.name }
        HistoryUiState(
            loading = false, currency = b.currency, groups = groups, filter = f, query = q,
            selected = selected, selectedAccountName = names[selected?.accountId] ?: "",
            totalCount = b.txs.size, csv = CsvExport.build(filtered, names)
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun setFilter(type: TxType?) { filter.value = type }
    fun setQuery(q: String) { query.value = q }
    fun select(id: Long?) { selectedId.value = id }

    fun delete(id: Long) {
        viewModelScope.launch {
            container.transactionRepository.delete(id)
            if (selectedId.value == id) selectedId.value = null
        }
    }
}

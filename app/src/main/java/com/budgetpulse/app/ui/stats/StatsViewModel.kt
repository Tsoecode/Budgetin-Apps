package com.budgetpulse.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.logic.Period
import com.budgetpulse.app.domain.logic.PeriodReport
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val period: Period = Period.MONTH,
    val offset: Int = 0,
    val report: PeriodReport? = null,
    val currency: String = "USD"
) {
    val canGoForward: Boolean get() = offset < 0
}

class StatsViewModel(container: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(Period.MONTH)
    private val offset = MutableStateFlow(0)

    val state: StateFlow<StatsUiState> = combine(
        container.transactionRepository.observeAll(), container.userPreferences.prefs, period, offset
    ) { txs, prefs, p, o ->
        StatsUiState(false, p, o, BudgetCalculator.report(txs, p, LocalDate.now(), o), prefs.currency)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun setPeriod(p: Period) {
        AppLog.d(AppLog.STATE, "stats period -> $p")
        period.value = p
        offset.value = 0
    }

    fun shift(delta: Int) {
        val next = offset.value + delta
        if (next <= 0) offset.value = next
    }
}

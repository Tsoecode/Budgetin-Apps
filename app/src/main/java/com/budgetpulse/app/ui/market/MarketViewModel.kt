package com.budgetpulse.app.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.logic.CurrencyConverter
import com.budgetpulse.app.domain.model.CurrencyRates
import com.budgetpulse.app.ui.common.UiState
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MarketUiState(
    val base: String = "USD",
    val target: String = "EUR",
    val amountText: String = "100",
    val query: String = "",
    val refreshing: Boolean = false,
    val rates: UiState<CurrencyRates> = UiState.Loading
) {
    /** Result of the converter card, or null when the amount is invalid / the currency unknown. */
    val converted: Double?
        get() {
            val data = (rates as? UiState.Success)?.data ?: return null
            val amount = amountText.replace(',', '.').toDoubleOrNull() ?: return null
            return CurrencyConverter.convert(amount, base, target, data.rates)
        }
}

class MarketViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    init {
        // Follows the main currency from Settings: change it there and the market switches to it immediately.
        viewModelScope.launch {
            container.userPreferences.prefs.map { it.currency }.distinctUntilChanged().collect { currency ->
                _state.update {
                    it.copy(
                        base = currency,
                        target = if (it.target == currency) (if (currency == "USD") "EUR" else "USD") else it.target,
                        rates = UiState.Loading
                    )
                }
                load(force = false)
            }
        }
        // While this screen is alive, re-check every 5 minutes. The repository only hits the network
        // when its cache has expired, so this respects the provider's rate limit.
        viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_MS)
                load(force = false)
            }
        }
    }

    fun load(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val base = _state.value.base
            val result = container.currencyRepository.getRates(base, force)
            AppLog.d(AppLog.STATE, "market load base=$base force=$force ok=${result.isSuccess}")
            _state.update { s ->
                if (s.base != base) s else s.copy(
                    refreshing = false,
                    rates = result.fold(
                        onSuccess = { UiState.Success(it, it.stale) },
                        onFailure = { UiState.Error(it.message ?: "Unable to load rates.") }
                    )
                )
            }
        }
    }

    fun setBase(code: String) {
        if (code == _state.value.base) return
        _state.update { it.copy(base = code, rates = UiState.Loading) }
        load(force = false)
    }

    fun setTarget(code: String) = _state.update { it.copy(target = code) }
    fun setAmount(text: String) = _state.update { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    companion object { private const val AUTO_REFRESH_MS = 5 * 60 * 1000L }
}

package com.budgetpulse.app.ui.tips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.model.NewsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TipsUiState(val news: List<NewsItem> = emptyList(), val refreshing: Boolean = false, val error: String? = null)

class TipsViewModel(private val container: AppContainer) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<TipsUiState> = combine(container.newsRepository.observeNews(), refreshing, error) { n, r, e ->
        TipsUiState(n, r, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TipsUiState())

    init { refresh(false) }

    fun refresh(force: Boolean) {
        viewModelScope.launch {
            refreshing.value = true
            error.value = container.newsRepository.refreshIfNeeded(force).exceptionOrNull()?.let { "Could not refresh the news. Showing saved stories." }
            refreshing.value = false
        }
    }
}

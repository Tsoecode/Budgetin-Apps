package com.budgetpulse.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.budgetpulse.app.email.EmailSyncScheduler
import com.budgetpulse.app.email.EmailSyncWorker
import com.budgetpulse.app.data.prefs.ThemeMode
import com.budgetpulse.app.data.prefs.UserPrefs
import com.budgetpulse.app.data.repository.ConversionPreview
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.logic.InitialSyncRange
import com.budgetpulse.app.domain.logic.SyncInterval
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

data class SyncUi(
    val running: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val showNoMonthTransactionsDialog: Boolean = false
)

/** Steps of "change the main currency": the user sees the live rate and must confirm before any data changes. */
sealed interface ConversionUi {
    data object Idle : ConversionUi
    data class Loading(val to: String) : ConversionUi
    data class Confirm(val preview: ConversionPreview) : ConversionUi
    data class Converting(val preview: ConversionPreview) : ConversionUi
    data class Failed(val to: String, val message: String) : ConversionUi
}

data class SettingsUiState(
    val loaded: Boolean = false,
    val prefs: UserPrefs = UserPrefs(),
    val accounts: List<Account> = emptyList(),
    val sync: SyncUi = SyncUi(),
    val notice: String? = null,
    val conversion: ConversionUi = ConversionUi.Idle
)

class SettingsViewModel(private val container: AppContainer, private val appContext: Context) : ViewModel() {
    private val sync = MutableStateFlow(SyncUi())
    private val notice = MutableStateFlow<String?>(null)
    private val conversion = MutableStateFlow<ConversionUi>(ConversionUi.Idle)

    val state: StateFlow<SettingsUiState> = combine(
        container.userPreferences.prefs, container.accountRepository.observeAll(), sync, notice, conversion
    ) { prefs, accounts, s, n, c -> SettingsUiState(true, prefs, accounts, s, n, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { container.userPreferences.setTheme(mode) } }

    /** Step 1: fetch the live rate and ask for confirmation. Nothing is changed yet. */
    fun requestCurrency(code: String) {
        viewModelScope.launch {
            if (code == container.userPreferences.prefs.first().currency) return@launch
            conversion.value = ConversionUi.Loading(code)
            conversion.value = container.currencyConversionRepository.preview(code).fold(
                onSuccess = { ConversionUi.Confirm(it) },
                onFailure = { ConversionUi.Failed(code, it.message ?: "Could not load the exchange rate.") }
            )
        }
    }

    /** Step 2: convert every stored amount in one database transaction, then switch the currency. */
    fun confirmConversion() {
        val confirm = conversion.value as? ConversionUi.Confirm ?: return
        viewModelScope.launch {
            conversion.value = ConversionUi.Converting(confirm.preview)
            container.currencyConversionRepository.convertAll(confirm.preview).fold(
                onSuccess = { s ->
                    conversion.value = ConversionUi.Idle
                    notice.value = "Converted " + s.transactions + " transactions, " + s.accounts + " accounts, " +
                        s.limits + " limits, " + s.goals + " goals and your savings target to " + confirm.preview.to + "."
                },
                onFailure = { conversion.value = ConversionUi.Failed(confirm.preview.to, it.message ?: "Conversion failed. Nothing was changed.") }
            )
        }
    }

    fun retryConversion() {
        val failed = conversion.value as? ConversionUi.Failed ?: return
        requestCurrency(failed.to)
    }

    fun dismissConversion() {
        if (conversion.value !is ConversionUi.Converting) conversion.value = ConversionUi.Idle
    }
    fun logout() { viewModelScope.launch { container.userPreferences.logout() } }

    fun saveProfile(name: String, email: String) {
        viewModelScope.launch {
            container.userPreferences.updateProfile(name, email)
            notice.value = "Profile updated."
        }
    }
    fun dismissNotice() { notice.value = null }

    fun saveEmail(
        enabled: Boolean,
        host: String,
        user: String,
        password: String,
        keywords: String,
        accountId: Long,
        intervalMinutes: Int,
        initialScanDays: Int = InitialSyncRange.DEFAULT.days
    ) {
        viewModelScope.launch {
            val encrypted = try {
                password.takeIf { it.isNotBlank() }?.let { container.secureStore.encrypt(it) }
            } catch (e: Exception) {
                AppLog.e(AppLog.EMAIL, "could not encrypt password", e)
                notice.value = "This device could not store the password securely."
                return@launch
            }
            container.userPreferences.saveEmailSettings(enabled, host, user, encrypted, keywords, accountId, intervalMinutes, initialScanDays)
            notice.value = if (enabled) "E-mail import saved. " + SyncInterval.fromMinutes(intervalMinutes).label + " while online." else "E-mail import saved (switched off)."
        }
    }

    fun setInitialScanDays(days: Int) {
        viewModelScope.launch {
            container.userPreferences.setInitialScanDays(days)
        }
    }

    fun forgetPassword() {
        viewModelScope.launch {
            container.userPreferences.clearEmailPassword()
            notice.value = "Saved password removed."
        }
    }

    fun syncNow(overrideInitialDays: Int? = null) {
        viewModelScope.launch {
            val savedAttempt = container.userPreferences.manualScanAttempt()
            val requestedDays = overrideInitialDays ?: when {
                savedAttempt == 7 -> 7
                savedAttempt == 30 -> 30
                else -> container.userPreferences.prefs.first().initialScanDays
            }
            val nextAttempt = if (overrideInitialDays != null || savedAttempt == 0) requestedDays else savedAttempt
            container.userPreferences.setManualScanAttempt(nextAttempt)
            container.userPreferences.setManualEmailScanPending(true)
            sync.value = SyncUi(running = true, message = "Manual email scan queued…")
            try {
                EmailSyncScheduler.enqueueManualScan(appContext, requestedDays)
            } catch (e: Exception) {
                container.userPreferences.setManualEmailScanPending(false)
                sync.value = SyncUi(running = false, message = e.message ?: "Could not queue email scan.", isError = true)
            }
        }
    }

    fun dismissNoMonthTransactionsDialog() {
        sync.update { it.copy(showNoMonthTransactionsDialog = false) }
    }

    fun observeManualScan(context: Context) {
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.getWorkInfosForUniqueWorkFlow(EmailSyncScheduler.MANUAL_SCAN).collect { infos ->
                val info = infos.firstOrNull() ?: run {
                    if (container.userPreferences.isManualEmailScanPending()) {
                        sync.value = SyncUi(running = true, message = "Manual email scan is queued…")
                    } else {
                        sync.value = SyncUi()
                    }
                    return@collect
                }
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                        sync.value = SyncUi(running = true, message = "Manual email scan is running…")
                    WorkInfo.State.SUCCEEDED -> {
                        val data = info.outputData
                        val imported = data.getInt(EmailSyncWorker.KEY_IMPORTED, 0)
                        if (imported == -1) {
                            sync.value = SyncUi(running = true, message = data.getString(EmailSyncWorker.KEY_SUMMARY))
                        } else {
                            val recovered = data.getBoolean(EmailSyncWorker.KEY_RECOVERED_MONTH, false)
                            sync.value = SyncUi(
                                running = false,
                                message = data.getString(EmailSyncWorker.KEY_SUMMARY),
                                showNoMonthTransactionsDialog = recovered && imported == 0
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        sync.value = SyncUi(
                            running = false,
                            message = info.outputData.getString(EmailSyncWorker.KEY_SUMMARY) ?: "Email scan failed. Please try again.",
                            isError = true
                        )
                    }
                    WorkInfo.State.CANCELLED -> sync.value = SyncUi(running = false)
                }
            }
        }
    }

    fun clearTransactions() {
        viewModelScope.launch {
            container.dataLock.withLock {
                container.transactionRepository.deleteAll()
                container.userPreferences.setLastEmailSync(0L, "")
            }
            sync.value = SyncUi()
            notice.value = "All transactions deleted."
        }
    }
}

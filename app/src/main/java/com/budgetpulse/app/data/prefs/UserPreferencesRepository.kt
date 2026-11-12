package com.budgetpulse.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.budgetpulse.app.domain.logic.CurrencyConversion
import com.budgetpulse.app.domain.logic.EmailParser
import com.budgetpulse.app.domain.logic.InitialSyncRange
import com.budgetpulse.app.domain.logic.SyncInterval
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

data class UserPrefs(
    val isLoggedIn: Boolean = false,
    val userName: String = "",
    val userEmail: String = "",
    val currency: String = "USD",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val monthlySavingsTarget: Double = 0.0,
    // e-mail import
    val emailImportEnabled: Boolean = false,
    val imapHost: String = "imap.gmail.com",
    val imapUser: String = "",
    val hasImapPassword: Boolean = false,
    val senderKeywords: String = EmailParser.DEFAULT_SENDER_KEYWORDS.joinToString(", "),
    val emailAccountId: Long = -1L,
    val emailIntervalMinutes: Int = SyncInterval.DEFAULT.minutes,
    val initialScanDays: Int = InitialSyncRange.DEFAULT.days,
    val lastEmailSyncAt: Long = 0L,
    val lastEmailSyncMessage: String = ""
)

/** Jetpack DataStore wrapper: preferences (theme, currency, savings target) + e-mail import settings. */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val LOGGED_IN = booleanPreferencesKey("logged_in")
        val NAME = stringPreferencesKey("user_name")
        val EMAIL = stringPreferencesKey("user_email")
        val CURRENCY = stringPreferencesKey("currency")
        val THEME = stringPreferencesKey("theme_mode")
        val SAVINGS_TARGET = doublePreferencesKey("monthly_savings_target")
        val MAIL_ENABLED = booleanPreferencesKey("mail_enabled")
        val MAIL_HOST = stringPreferencesKey("mail_host")
        val MAIL_USER = stringPreferencesKey("mail_user")
        val MAIL_PASSWORD_ENC = stringPreferencesKey("mail_password_enc")
        val MAIL_KEYWORDS = stringPreferencesKey("mail_keywords")
        val MAIL_ACCOUNT = longPreferencesKey("mail_account_id")
        val MAIL_INTERVAL = intPreferencesKey("mail_interval_minutes")
        val MAIL_INITIAL_SCAN_DAYS = intPreferencesKey("mail_initial_scan_days")
        val MAIL_LAST_SYNC = longPreferencesKey("mail_last_sync")
        val MAIL_LAST_MSG = stringPreferencesKey("mail_last_msg")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { it.toUserPrefs() }

    private fun Preferences.toUserPrefs(): UserPrefs {
        val d = UserPrefs()
        return UserPrefs(
            isLoggedIn = this[Keys.LOGGED_IN] ?: false,
            userName = this[Keys.NAME] ?: "",
            userEmail = this[Keys.EMAIL] ?: "",
            currency = this[Keys.CURRENCY] ?: d.currency,
            themeMode = runCatching { ThemeMode.valueOf(this[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            monthlySavingsTarget = this[Keys.SAVINGS_TARGET] ?: 0.0,
            emailImportEnabled = this[Keys.MAIL_ENABLED] ?: false,
            imapHost = this[Keys.MAIL_HOST] ?: d.imapHost,
            imapUser = this[Keys.MAIL_USER] ?: "",
            hasImapPassword = !this[Keys.MAIL_PASSWORD_ENC].isNullOrBlank(),
            senderKeywords = this[Keys.MAIL_KEYWORDS] ?: d.senderKeywords,
            emailAccountId = this[Keys.MAIL_ACCOUNT] ?: -1L,
            emailIntervalMinutes = SyncInterval.fromMinutes(this[Keys.MAIL_INTERVAL] ?: SyncInterval.DEFAULT.minutes).minutes,
            initialScanDays = this[Keys.MAIL_INITIAL_SCAN_DAYS] ?: InitialSyncRange.DEFAULT.days,
            lastEmailSyncAt = this[Keys.MAIL_LAST_SYNC] ?: 0L,
            lastEmailSyncMessage = this[Keys.MAIL_LAST_MSG] ?: ""
        )
    }

    suspend fun completeOnboarding(name: String, email: String, currency: String) {
        context.dataStore.edit {
            it[Keys.NAME] = name.trim()
            it[Keys.EMAIL] = email.trim()
            it[Keys.CURRENCY] = currency
            it[Keys.LOGGED_IN] = true
        }
        AppLog.i(AppLog.STATE, "onboarding complete, currency=$currency")
    }

    /** Edit the account shown in Settings (name + e-mail). Nothing else is touched. */
    suspend fun updateProfile(name: String, email: String) {
        context.dataStore.edit {
            it[Keys.NAME] = name.trim()
            it[Keys.EMAIL] = email.trim()
        }
        AppLog.i(AppLog.STATE, "profile updated")
    }

    suspend fun logout() {
        context.dataStore.edit { it[Keys.LOGGED_IN] = false }
        AppLog.i(AppLog.STATE, "user logged out")
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
        AppLog.d(AppLog.STATE, "theme -> $mode")
    }

    suspend fun setCurrency(code: String) {
        context.dataStore.edit { it[Keys.CURRENCY] = code }
        AppLog.d(AppLog.STATE, "currency -> $code")
    }

    /**
     * Last step of a currency switch (the database rows were already converted): store the new currency and
     * convert the monthly savings target, in ONE DataStore edit so the two can never disagree.
     */
    suspend fun applyCurrencyConversion(newCurrency: String, factor: Double) {
        context.dataStore.edit {
            val oldTarget = it[Keys.SAVINGS_TARGET] ?: 0.0
            it[Keys.SAVINGS_TARGET] = CurrencyConversion.convert(oldTarget, factor, newCurrency)
            it[Keys.CURRENCY] = newCurrency
        }
        AppLog.i(AppLog.STATE, "currency converted -> $newCurrency (factor=$factor)")
    }

    suspend fun setMonthlySavingsTarget(value: Double) {
        context.dataStore.edit { it[Keys.SAVINGS_TARGET] = value }
        AppLog.d(AppLog.STATE, "monthly savings target -> $value")
    }

    /** [encryptedPassword] == null keeps the existing password. */
    suspend fun saveEmailSettings(
        enabled: Boolean,
        host: String,
        user: String,
        encryptedPassword: String?,
        keywords: String,
        accountId: Long,
        intervalMinutes: Int,
        initialScanDays: Int = InitialSyncRange.DEFAULT.days
    ) {
        context.dataStore.edit {
            it[Keys.MAIL_ENABLED] = enabled
            it[Keys.MAIL_HOST] = host.trim()
            it[Keys.MAIL_USER] = user.trim()
            if (encryptedPassword != null) it[Keys.MAIL_PASSWORD_ENC] = encryptedPassword
            it[Keys.MAIL_KEYWORDS] = keywords
            it[Keys.MAIL_ACCOUNT] = accountId
            it[Keys.MAIL_INTERVAL] = intervalMinutes
            it[Keys.MAIL_INITIAL_SCAN_DAYS] = initialScanDays
        }
        AppLog.i(AppLog.EMAIL, "email import settings saved enabled=$enabled host=$host every=${intervalMinutes}min initialScanDays=$initialScanDays")
    }

    suspend fun setInitialScanDays(days: Int) {
        context.dataStore.edit { it[Keys.MAIL_INITIAL_SCAN_DAYS] = days }
        AppLog.d(AppLog.EMAIL, "initial scan lookback set to $days days")
    }

    suspend fun clearEmailPassword() {
        context.dataStore.edit { it.remove(Keys.MAIL_PASSWORD_ENC) }
    }

    suspend fun encryptedImapPassword(): String? = context.dataStore.data.first()[Keys.MAIL_PASSWORD_ENC]

    suspend fun setLastEmailSync(at: Long, message: String) {
        context.dataStore.edit {
            it[Keys.MAIL_LAST_SYNC] = at
            it[Keys.MAIL_LAST_MSG] = message
        }
    }
}

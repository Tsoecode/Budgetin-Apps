package com.budgetpulse.app.di

import android.content.Context
import com.budgetpulse.app.data.local.AppDatabase
import com.budgetpulse.app.data.prefs.SecureStore
import com.budgetpulse.app.data.prefs.UserPreferencesRepository
import com.budgetpulse.app.data.remote.ExchangeRateApi
import com.budgetpulse.app.data.remote.FrankfurterApi
import com.budgetpulse.app.data.remote.NetworkLogInterceptor
import com.budgetpulse.app.data.remote.NewsRemoteSource
import com.budgetpulse.app.data.repository.AccountRepository
import com.budgetpulse.app.data.repository.BudgetRepository
import com.budgetpulse.app.data.repository.CurrencyConversionRepository
import com.budgetpulse.app.data.repository.CurrencyRepository
import com.budgetpulse.app.data.repository.EmailImportRepository
import com.budgetpulse.app.data.repository.NewsRepository
import com.budgetpulse.app.data.repository.NotificationRepository
import com.budgetpulse.app.data.repository.TransactionRepository
import com.budgetpulse.app.email.EmailFetcher
import com.budgetpulse.app.email.ImapEmailFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Manual dependency injection: one container, created once in [com.budgetpulse.app.BudgetPulseApp]. */
interface AppContainer {
    val applicationScope: CoroutineScope
    val userPreferences: UserPreferencesRepository
    val accountRepository: AccountRepository
    val transactionRepository: TransactionRepository
    val notificationRepository: NotificationRepository
    val budgetRepository: BudgetRepository
    val currencyRepository: CurrencyRepository
    val newsRepository: NewsRepository
    val emailImportRepository: EmailImportRepository
    val currencyConversionRepository: CurrencyConversionRepository
    val dataLock: Mutex
    val secureStore: SecureStore
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { AppDatabase.build(appContext) }
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(NetworkLogInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val exchangeRateApi by lazy { retrofit("https://open.er-api.com/").create(ExchangeRateApi::class.java) }
    private val frankfurterApi by lazy { retrofit("https://api.frankfurter.dev/").create(FrankfurterApi::class.java) }

    override val secureStore by lazy { SecureStore() }
    private val emailFetcher: EmailFetcher by lazy { ImapEmailFetcher() }

    override val userPreferences by lazy { UserPreferencesRepository(appContext) }
    override val accountRepository by lazy { AccountRepository(database.accountDao()) }
    override val notificationRepository by lazy { NotificationRepository(database.notificationDao()) }
    override val transactionRepository by lazy { TransactionRepository(database.transactionDao(), notificationRepository) }
    override val budgetRepository by lazy { BudgetRepository(database.budgetDao(), database.goalDao()) }
    override val currencyRepository by lazy { CurrencyRepository(exchangeRateApi, frankfurterApi, database.ratesDao()) }
    override val newsRepository by lazy { NewsRepository(database.newsDao(), NewsRemoteSource(httpClient)) }
    override val dataLock = Mutex()
    override val emailImportRepository by lazy {
        EmailImportRepository(userPreferences, secureStore, emailFetcher, transactionRepository, accountRepository, currencyRepository, dataLock)
    }
    override val currencyConversionRepository by lazy {
        CurrencyConversionRepository(database, userPreferences, currencyRepository, dataLock)
    }
}

package com.budgetpulse.app.ui.onboarding

import com.budgetpulse.app.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.logic.MailProviders
import com.budgetpulse.app.ui.common.*
import com.budgetpulse.app.ui.theme.BrandOrange
import com.budgetpulse.app.ui.theme.BrandPeach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

data class OnboardingUi(
    val busy: Boolean = false,
    val error: String? = null,
    /** Set when the device already holds data (sign-out then sign-in): the currency that data is stored in. */
    val storedCurrency: String? = null
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui: StateFlow<OnboardingUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val hasData = container.accountRepository.observeAll().first().isNotEmpty()
            if (hasData) {
                val stored = container.userPreferences.prefs.first().currency
                _ui.update { it.copy(storedCurrency = stored) }
            }
        }
    }

    /**
     * Finishes onboarding. If saved data exists in another currency than the one picked, ALL of it is
     * converted first (live rate); if that fails nothing changes and the user stays on this screen.
     */
    fun finish(name: String, email: String, currency: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            val stored = _ui.value.storedCurrency
            if (stored != null && stored != currency) {
                val conversion = container.currencyConversionRepository
                val preview = conversion.preview(currency).getOrElse { e ->
                    _ui.update { it.copy(busy = false, error = e.message ?: "Could not load the exchange rate.") }
                    return@launch
                }
                conversion.convertAll(preview).getOrElse { e ->
                    _ui.update { it.copy(busy = false, error = e.message ?: "Conversion failed. Nothing was changed.") }
                    return@launch
                }
            }
            container.accountRepository.seedDefaultsIfEmpty()
            // Flipping isLoggedIn makes the nav root redirect to Home (dynamic navigation).
            container.userPreferences.completeOnboarding(name, email, currency)
        }
    }
}

val SUPPORTED_CURRENCIES = listOf(
    "IDR", "MYR", "USD", "EUR", "GBP", "SGD", "JPY", "AUD", "CNY", "KRW", "INR", "THB", "PHP", "VND", "HKD", "CAD", "CHF", "NZD", "SAR", "AED"
)

fun currencyLabel(code: String): String =
    runCatching { code + " - " + Currency.getInstance(code).getDisplayName(Locale.ENGLISH) }.getOrDefault(code)

/** Friendly mascot: a flat vector from res/drawable (cheap to draw, no per-frame Canvas work). */
@Composable
fun Mascot(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.img_mascot),
        contentDescription = "Budgetin mascot: a friendly orange character holding a coin",
        modifier = modifier
    )
}

@Composable
private fun AuthBackground(footer: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize().padding(24.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
            Spacer(Modifier.height(16.dp))
            footer()
        }
    }
}

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    AuthBackground(footer = { PillButton("Get Started", onGetStarted, Modifier.fillMaxWidth()) }) {
        Spacer(Modifier.height(32.dp))
        Mascot(Modifier.size(240.dp))
        Spacer(Modifier.height(24.dp))
        Text("Hi! I'm Budi!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Your financial assistant", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            "I'll help you easily manage your money, plan budgets and achieve your financial goals.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SignInScreen(onBack: () -> Unit, onContinue: (name: String, email: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    val valid = name.isNotBlank() && MailProviders.isValidEmail(email)
    AuthBackground(
        footer = { PillButton("Continue", { onContinue(name.trim(), email.trim()) }, Modifier.fillMaxWidth(), enabled = valid) }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                AppIcon(R.drawable.ic_arrow_back, contentDescription = "Back")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Make your financial behavior better!", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Create your profile to start your journey toward financial independence.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Your name") }, singleLine = true,
            leadingIcon = { AppIcon(R.drawable.ic_person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
            leadingIcon = { AppIcon(R.drawable.ic_email, contentDescription = null) },
            isError = email.isNotEmpty() && !MailProviders.isValidEmail(email),
            supportingText = { Text("Stored on this device only.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickCurrencyScreen(name: String, email: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = appViewModel { OnboardingViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val default = remember {
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
            ?.takeIf { it in SUPPORTED_CURRENCIES } ?: "USD"
    }
    var selected by rememberSaveable { mutableStateOf(default) }
    var expanded by remember { mutableStateOf(false) }
    // Returning user: start from the currency the saved data is in, so nothing is converted by accident.
    LaunchedEffect(ui.storedCurrency) { ui.storedCurrency?.let { selected = it } }
    val willConvert = ui.storedCurrency != null && ui.storedCurrency != selected

    AuthBackground(
        footer = {
            PillButton(
                if (ui.busy) "Please wait..." else "Continue", enabled = !ui.busy, modifier = Modifier.fillMaxWidth(),
                onClick = { vm.finish(name, email, selected) }
            )
        }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                AppIcon(R.drawable.ic_arrow_back, contentDescription = "Back")
            }
        }
        Mascot(Modifier.size(160.dp))
        Spacer(Modifier.height(8.dp))
        Text("Nice to meet you, ${name.substringBefore(' ')}!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "Choose the main currency. Balances and transactions are shown in it.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = currencyLabel(selected), onValueChange = {}, readOnly = true, label = { Text("Main currency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SUPPORTED_CURRENCIES.forEach { code ->
                    DropdownMenuItem(text = { Text(currencyLabel(code)) }, onClick = { selected = code; expanded = false })
                }
            }
        }
        if (ui.storedCurrency != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (willConvert) "Your saved data is in ${ui.storedCurrency}. Continuing converts all of it to $selected at today's live rate."
                else "Your saved data is already in ${ui.storedCurrency}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        InlineError(ui.error)
    }
}

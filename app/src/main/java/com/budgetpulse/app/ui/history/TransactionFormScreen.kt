package com.budgetpulse.app.ui.history

import com.budgetpulse.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.ui.common.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FormState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val type: TxType = TxType.EXPENSE,
    val amountText: String = "",
    val category: Category = Category.FOOD,
    val note: String = "",
    val accountId: Long = -1L,
    val timestamp: Long = System.currentTimeMillis(),
    val accounts: List<Account> = emptyList(),
    val currency: String = "USD",
    val error: String? = null,
    val source: TxSource = TxSource.MANUAL,
    val externalId: String? = null
)

class TransactionFormViewModel(
    private val container: AppContainer,
    private val id: Long,
    startAsIncome: Boolean
) : ViewModel() {

    private val _state = MutableStateFlow(
        FormState(
            type = if (startAsIncome) TxType.INCOME else TxType.EXPENSE,
            category = if (startAsIncome) Category.SALARY else Category.FOOD
        )
    )
    val state: StateFlow<FormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = container.accountRepository.observeAll().first()
            val prefs = container.userPreferences.prefs.first()
            val existing = if (id >= 0) container.transactionRepository.get(id) else null
            _state.update { s ->
                if (existing != null) {
                    s.copy(
                        loaded = true, isEdit = true, accounts = accounts, currency = prefs.currency,
                        type = existing.type, amountText = trimZeros(existing.amount), category = existing.category,
                        note = existing.note, accountId = existing.accountId, timestamp = existing.timestamp,
                        source = existing.source, externalId = existing.externalId
                    )
                } else {
                    s.copy(loaded = true, accounts = accounts, currency = prefs.currency, accountId = accounts.firstOrNull()?.id ?: -1L)
                }
            }
        }
    }

    private fun trimZeros(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun setType(t: TxType) = _state.update { s ->
        s.copy(type = t, category = if (s.category.type == t) s.category else Category.forType(t).first(), error = null)
    }
    fun setAmount(text: String) = _state.update { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null) }
    fun setCategory(c: Category) = _state.update { it.copy(category = c) }
    fun setNote(n: String) = _state.update { it.copy(note = n.take(80)) }
    fun setAccount(id: Long) = _state.update { it.copy(accountId = id) }

    /** [utcMidnight] is what the Material date picker returns; keep the original time of day. */
    fun setDate(utcMidnight: Long) = _state.update { s ->
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(utcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(s.timestamp).atZone(zone).toLocalTime()
        s.copy(timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val amount = s.amountText.replace(',', '.').toDoubleOrNull()
        when {
            amount == null || amount <= 0.0 -> { _state.update { it.copy(error = "Enter an amount greater than zero.") }; return }
            s.accountId < 0 -> { _state.update { it.copy(error = "Choose an account.") }; return }
        }
        viewModelScope.launch {
            val tx = Transaction(
                id = if (s.isEdit) id else 0, accountId = s.accountId, type = s.type, amount = amount!!,
                category = s.category, note = s.note.trim(), timestamp = s.timestamp, source = s.source, externalId = s.externalId
            )
            if (s.isEdit) container.transactionRepository.update(tx) else container.transactionRepository.add(tx)
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(transactionId: Long, startAsIncome: Boolean, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = appViewModel(key = "form-$transactionId-$startAsIncome") { TransactionFormViewModel(container, transactionId, startAsIncome) }
    val s by vm.state.collectAsStateWithLifecycle()
    var accountMenu by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    ScreenScaffold(title = if (s.isEdit) "Edit transaction" else "Add transaction", onBack = onClose) {
        if (!s.loaded) {
            LoadingBox()
        } else {
            Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(s.type == TxType.EXPENSE, { vm.setType(TxType.EXPENSE) }, label = { Text("Spend") }, modifier = Modifier.testTag("chip_expense"))
                        FilterChip(s.type == TxType.INCOME, { vm.setType(TxType.INCOME) }, label = { Text("Income") }, modifier = Modifier.testTag("chip_income"))
                    }
                    OutlinedTextField(
                        value = s.amountText, onValueChange = vm::setAmount, label = { Text("Amount (${s.currency})") }, singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = s.error != null, modifier = Modifier.fillMaxWidth().testTag("field_amount")
                    )
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Category.forType(s.type).forEach { c ->
                            FilterChip(
                                selected = s.category == c, onClick = { vm.setCategory(c) }, label = { Text(c.label) },
                                leadingIcon = { AppIcon(c.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    ExposedDropdownMenuBox(expanded = accountMenu, onExpandedChange = { accountMenu = it }) {
                        OutlinedTextField(
                            value = s.accounts.firstOrNull { it.id == s.accountId }?.name ?: "No account",
                            onValueChange = {}, readOnly = true, label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                            s.accounts.forEach { a ->
                                DropdownMenuItem(text = { Text(a.name) }, onClick = { vm.setAccount(a.id); accountMenu = false })
                            }
                        }
                    }
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                        AppIcon(R.drawable.ic_calendar_month, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH).format(Instant.ofEpochMilli(s.timestamp).atZone(ZoneId.systemDefault())))
                    }
                    OutlinedTextField(
                        value = s.note, onValueChange = vm::setNote, label = { Text("What was it?") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("field_note")
                    )
                    InlineError(s.error)
                    PillButton("Save", { vm.save(onClose) }, Modifier.fillMaxWidth().testTag("btn_save"))
                }
            }
        }
    }

    if (showDate) {
        val zone = ZoneId.systemDefault()
        val initial = Instant.ofEpochMilli(s.timestamp).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let(vm::setDate); showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

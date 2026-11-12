package com.budgetpulse.app.ui.settings

import com.budgetpulse.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpulse.app.data.prefs.ThemeMode
import com.budgetpulse.app.domain.logic.InitialSyncRange
import com.budgetpulse.app.domain.logic.MailProviders
import com.budgetpulse.app.domain.logic.SyncInterval
import com.budgetpulse.app.ui.common.*
import com.budgetpulse.app.ui.onboarding.SUPPORTED_CURRENCIES
import com.budgetpulse.app.domain.logic.RateDisplay
import com.budgetpulse.app.util.label
import com.budgetpulse.app.ui.onboarding.currencyLabel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(widthClass: WindowWidthSizeClass) {
    val container = LocalAppContainer.current
    val vm = appViewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf(false) }

    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); vm.dismissNotice() }
    }

    ScreenScaffold(title = "Settings") {
        if (!state.loaded) {
            LoadingBox()
            return@ScreenScaffold
        }
        val p = state.prefs
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- profile
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(R.drawable.ic_account_circle, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.userName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(p.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editProfile = true }, modifier = Modifier.testTag("btn_edit_profile")) {
                                AppIcon(R.drawable.ic_edit, contentDescription = "Edit profile")
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { confirmLogout = true }, modifier = Modifier.testTag("btn_logout")) {
                                AppIcon(R.drawable.ic_logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text("Sign out")
                            }
                        }
                    }
                }

                // ---- appearance + currency
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { m ->
                                FilterChip(
                                    selected = p.themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(m.label) },
                                    modifier = Modifier.testTag("theme_" + m.name)
                                )
                            }
                        }
                        var open by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                            OutlinedTextField(
                                value = currencyLabel(p.currency), onValueChange = {}, readOnly = true, label = { Text("Main currency") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                SUPPORTED_CURRENCIES.forEach { c ->
                                    DropdownMenuItem(text = { Text(currencyLabel(c)) }, onClick = { vm.requestCurrency(c); open = false })
                                }
                            }
                        }
                        Text(
                            "Changing the currency converts every saved amount (transactions, balances, limits, goals, savings target) at today's exchange rate.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ---- e-mail import
                EmailImportCard(state, vm)

                // ---- data
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Everything is stored on this device only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            AppIcon(R.drawable.ic_delete_forever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text("Delete all transactions")
                        }
                    }
                }

                // ---- about / credits
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("About Budgetin 1.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Exchange rates: ExchangeRate-API (rates by Exchange Rate API), fallback ECB via Frankfurter.", style = MaterialTheme.typography.bodySmall)
                        Text("News: Google News. Country flags: flagcdn.com. Savings guide: Bank Jago.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { openUrl(context, "https://www.exchangerate-api.com") }, contentPadding = PaddingValues(0.dp)) {
                            Text("Rates By Exchange Rate API")
                        }
                    }
                }
                Spacer(Modifier.height(72.dp + LocalBottomInset.current))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false }, title = { Text("Delete all transactions?") },
            text = { Text("This cannot be undone. Accounts, limits and goals are kept.") },
            confirmButton = { TextButton(onClick = { vm.clearTransactions(); confirmClear = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
    CurrencyConversionDialog(
        conversion = state.conversion,
        onConfirm = vm::confirmConversion, onRetry = vm::retryConversion, onDismiss = vm::dismissConversion
    )
    if (editProfile) {
        EditProfileDialog(
            initialName = state.prefs.userName, initialEmail = state.prefs.userEmail,
            onSave = { n, e -> vm.saveProfile(n, e); editProfile = false },
            onDismiss = { editProfile = false }
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false }, title = { Text("Sign out?") },
            text = { Text("Your data stays on this device. You can sign in again at any time.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }, modifier = Modifier.testTag("confirm_logout")) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailImportCard(state: SettingsUiState, vm: SettingsViewModel) {
    val p = state.prefs
    var enabled by remember(state.loaded) { mutableStateOf(p.emailImportEnabled) }
    var user by remember(state.loaded) { mutableStateOf(p.imapUser.ifBlank { p.userEmail }) }
    var host by remember(state.loaded) { mutableStateOf(p.imapHost) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var keywords by remember(state.loaded) { mutableStateOf(p.senderKeywords) }
    var accountId by remember(state.loaded) { mutableStateOf(p.emailAccountId) }
    var accountMenu by remember { mutableStateOf(false) }
    var interval by remember(state.loaded) { mutableStateOf(SyncInterval.fromMinutes(p.emailIntervalMinutes)) }
    var intervalMenu by remember { mutableStateOf(false) }
    var initialRange by remember(state.loaded) { mutableStateOf(InitialSyncRange.fromDays(p.initialScanDays)) }
    var showInitialSyncDialog by remember { mutableStateOf(false) }
    val target = state.accounts.firstOrNull { it.id == accountId } ?: state.accounts.firstOrNull()
    val outlook = MailProviders.isOutlookFamily(user)

    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_mark_email_read, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Import from m-banking e-mails", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Turns bank notification e-mails into income or expense transactions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it }, modifier = Modifier.testTag("switch_email"))
            }
            if (enabled) {
                OutlinedTextField(
                    value = user, singleLine = true, label = { Text("E-mail address") },
                    onValueChange = { user = it; MailProviders.suggestImapHost(it)?.let { h -> host = h } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().testTag("field_mail_user")
                )
                if (outlook) {
                    Text(
                        "Microsoft accounts usually block app-password IMAP logins. A Gmail, Yahoo or iCloud mailbox (e.g. one that receives your bank alerts) works best.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text(if (p.hasImapPassword) "App password (saved - leave blank to keep)" else "App password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            AppIcon(if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility, contentDescription = if (showPassword) "Hide password" else "Show password")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("field_mail_password")
                )
                OutlinedTextField(value = host, onValueChange = { host = it }, singleLine = true, label = { Text("IMAP server") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = keywords, onValueChange = { keywords = it }, label = { Text("Only read mails from senders containing") },
                    supportingText = { Text("Comma separated, e.g. bca, mandiri, maybank, gopay") }, modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = accountMenu, onExpandedChange = { accountMenu = it }) {
                    OutlinedTextField(
                        value = target?.name ?: "No account", onValueChange = {}, readOnly = true, label = { Text("Add imported transactions to") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        state.accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { accountId = a.id; accountMenu = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = intervalMenu, onExpandedChange = { intervalMenu = it }) {
                    OutlinedTextField(
                        value = interval.label, onValueChange = {}, readOnly = true, label = { Text("Check for new e-mails") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("dropdown_interval")
                    )
                    ExposedDropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                        SyncInterval.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { interval = option; intervalMenu = false })
                        }
                    }
                }
                Text(
                    "How it works: Budgetin signs in read-only over IMAP, looks at the last days of mail from your bank senders, and recognises debit / credit wording and the amount. " +
                        "Each e-mail is imported once. OTP, promo and statement mails are ignored. Your app password is encrypted with the Android Keystore. " +
                        "Gmail: turn on 2-Step Verification, then create an App password under Google Account > Security.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveEmail(enabled, host, user, password, keywords, target?.id ?: -1L, interval.minutes, p.initialScanDays); password = "" },
                    modifier = Modifier.weight(1f).testTag("btn_save_email")
                ) { Text("Save") }
                if (enabled) {
                    OutlinedButton(
                        onClick = {
                            if (p.lastEmailSyncAt == 0L) {
                                showInitialSyncDialog = true
                            } else {
                                vm.syncNow()
                            }
                        },
                        enabled = p.hasImapPassword && !state.sync.running,
                        modifier = Modifier.weight(1f).testTag("btn_sync_now")
                    ) {
                        if (state.sync.running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else AppIcon(R.drawable.ic_sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Sync now")
                    }
                }
            }
            state.sync.message?.let { InlineOrInfo(it, state.sync.isError) }
            if (p.lastEmailSyncAt > 0) {
                Text(
                    "Last check: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.lastEmailSyncAt)) +
                        if (p.lastEmailSyncMessage.isNotBlank()) " - " + p.lastEmailSyncMessage else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (p.hasImapPassword) TextButton(onClick = vm::forgetPassword) { Text("Remove saved password") }
        }
    }

    if (showInitialSyncDialog) {
        AlertDialog(
            onDismissRequest = { showInitialSyncDialog = false },
            title = { Text("Initial e-mail scan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select how far back to scan your mailbox for transactions:", style = MaterialTheme.typography.bodyMedium)
                    InitialSyncRange.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { initialRange = option }
                        ) {
                            RadioButton(
                                selected = initialRange == option,
                                onClick = { initialRange = option }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(option.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (option == InitialSyncRange.ONE_WEEK) "Scans the past 7 days of e-mails" else "Scans the past 30 days of e-mails",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInitialSyncDialog = false
                    vm.syncNow(initialRange.days)
                }) {
                    Text("Scan now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInitialSyncDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InlineOrInfo(text: String, isError: Boolean) {
    if (isError) InlineError(text) else Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
}

/** Shows the live rate and asks for confirmation before all stored amounts are converted. */
@Composable
private fun CurrencyConversionDialog(
    conversion: ConversionUi,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    when (conversion) {
        ConversionUi.Idle -> Unit
        is ConversionUi.Loading -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Checking today's rate") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Loading the live exchange rate for " + conversion.to + "...")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        is ConversionUi.Confirm -> {
            val p = conversion.preview
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Convert everything to " + p.to + "?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(RateDisplay.fromFactor(p.from, p.to, p.factor).label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("conversion_rate"))
                        Text("Updated: " + p.updatedLabel + "  (" + p.provider + ")", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "All saved amounts become their equal value in " + p.to + ": transactions, account balances, category limits, " +
                                "savings goals and your monthly savings target. Values are rounded to the currency's decimals, so switching back " +
                                "may differ by a tiny amount."
                        )
                    }
                },
                confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("btn_convert_confirm")) { Text("Convert") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
            )
        }
        is ConversionUi.Converting -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Converting your data") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Please wait...")
                }
            },
            confirmButton = {}
        )
        is ConversionUi.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Currency not changed") },
            text = { Text(conversion.message + "\n\nYour data was not modified.") },
            confirmButton = { TextButton(onClick = onRetry) { Text("Try again") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}

@Composable
private fun EditProfileDialog(initialName: String, initialEmail: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    val emailOk = MailProviders.isValidEmail(email)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text("Your name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("field_profile_name")
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true,
                    isError = email.isNotEmpty() && !emailOk,
                    supportingText = { Text("Only stored on this device.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("field_profile_email")
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && emailOk, onClick = { onSave(name.trim(), email.trim()) }, modifier = Modifier.testTag("btn_save_profile")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

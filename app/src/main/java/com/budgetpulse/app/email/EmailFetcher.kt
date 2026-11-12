package com.budgetpulse.app.email

import com.budgetpulse.app.domain.model.RawEmail

data class ImapConfig(val host: String, val user: String, val password: String)

class EmailAuthException(message: String) : Exception(message)

/** Abstraction over the mail server so the import logic can be tested with a fake. */
interface EmailFetcher {
    /** Newest messages received at or after [sinceMillis] (max [max]). Throws [EmailAuthException] or IOException. */
    suspend fun fetchRecent(config: ImapConfig, sinceMillis: Long, max: Int): List<RawEmail>
}

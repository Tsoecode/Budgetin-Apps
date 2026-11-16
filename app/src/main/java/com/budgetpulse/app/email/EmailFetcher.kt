package com.budgetpulse.app.email

import com.budgetpulse.app.domain.model.RawEmail

data class ImapConfig(val host: String, val user: String, val password: String)

class EmailAuthException(message: String) : Exception(message)

/** Abstraction over the mail server so the import logic can be tested with a fake. */
interface EmailFetcher {
    /** All messages received at or after [sinceMillis]. Implementations must not silently truncate results. */
    suspend fun fetchRecent(config: ImapConfig, sinceMillis: Long): List<RawEmail>
}

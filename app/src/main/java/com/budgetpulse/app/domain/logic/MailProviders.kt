package com.budgetpulse.app.domain.logic

/** Helps the Settings page pre-fill the IMAP server from the e-mail address. */
object MailProviders {
    private val known = mapOf(
        "gmail.com" to "imap.gmail.com",
        "googlemail.com" to "imap.gmail.com",
        "yahoo.com" to "imap.mail.yahoo.com",
        "ymail.com" to "imap.mail.yahoo.com",
        "icloud.com" to "imap.mail.me.com",
        "me.com" to "imap.mail.me.com",
        "zoho.com" to "imap.zoho.com"
    )

    fun domainOf(email: String): String? =
        email.substringAfter('@', "").trim().lowercase().takeIf { it.contains('.') }

    fun suggestImapHost(email: String): String? {
        val domain = domainOf(email) ?: return null
        return known[domain] ?: if (isOutlookFamily(email)) "outlook.office365.com" else "imap.$domain"
    }

    /** Microsoft accounts no longer accept plain app-password IMAP logins in most cases. */
    fun isOutlookFamily(email: String): Boolean {
        val d = domainOf(email) ?: return false
        return d == "outlook.com" || d == "hotmail.com" || d == "live.com" || d == "msn.com"
    }

    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())
}

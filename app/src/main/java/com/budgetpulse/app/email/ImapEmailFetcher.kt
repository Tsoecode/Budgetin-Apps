package com.budgetpulse.app.email

import com.budgetpulse.app.domain.model.RawEmail
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Date
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

/** Reads the INBOX over IMAPS in READ-ONLY mode. Nothing is ever deleted, moved or marked as read. */
class ImapEmailFetcher : EmailFetcher {

    override suspend fun fetchRecent(config: ImapConfig, sinceMillis: Long, max: Int): List<RawEmail> =
        withContext(Dispatchers.IO) {
            val props = Properties().apply {
                setProperty("mail.store.protocol", "imaps")
                setProperty("mail.imaps.ssl.enable", "true")
                setProperty("mail.imaps.port", "993")
                setProperty("mail.imaps.connectiontimeout", "15000")
                setProperty("mail.imaps.timeout", "20000")
            }
            val store = Session.getInstance(props).getStore("imaps")
            try {
                store.connect(config.host, config.user, config.password)
            } catch (e: AuthenticationFailedException) {
                throw EmailAuthException("Login rejected. Use an app password, not your normal password.")
            } catch (e: MessagingException) {
                throw IOException("Cannot reach ${config.host}: ${e.message}", e)
            }
            try {
                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)
                try {
                    val found = inbox.search(ReceivedDateTerm(ComparisonTerm.GE, Date(sinceMillis)))
                    AppLog.d(AppLog.EMAIL, "IMAP search returned ${found.size} messages")
                    found.takeLast(max).mapNotNull { toRaw(it) }
                } finally {
                    runCatching { inbox.close(false) }
                }
            } finally {
                runCatching { store.close() }
            }
        }

    private fun toRaw(m: Message): RawEmail? = try {
        val id = m.getHeader("Message-ID")?.firstOrNull()?.trim().orEmpty()
        val first = m.from?.firstOrNull()
        val from = (first as? InternetAddress)?.let { "${it.personal.orEmpty()} <${it.address}>" } ?: first?.toString().orEmpty()
        val received = (m.receivedDate ?: m.sentDate ?: Date()).time
        RawEmail(
            messageId = id.ifBlank { "$from|$received|${m.subject.orEmpty().hashCode()}" },
            from = from,
            subject = m.subject.orEmpty(),
            body = bodyText(m).take(4000),
            receivedAt = received
        )
    } catch (e: Exception) {
        AppLog.w(AppLog.EMAIL, "skipping unreadable message: ${e.message}")
        null
    }

    private fun bodyText(part: Part): String = when {
        part.isMimeType("text/plain") -> (part.content as? String).orEmpty()
        part.isMimeType("text/html") -> stripHtml((part.content as? String).orEmpty())
        part.isMimeType("multipart/*") -> {
            val mp = part.content as Multipart
            (0 until mp.count).map { bodyText(mp.getBodyPart(it)) }.firstOrNull { it.isNotBlank() }.orEmpty()
        }
        else -> ""
    }

    private fun stripHtml(html: String): String = html
        .replace(Regex("(?is)<(script|style).*?</\\1>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
}

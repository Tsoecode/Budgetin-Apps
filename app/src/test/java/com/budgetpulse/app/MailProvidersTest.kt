package com.budgetpulse.app

import com.budgetpulse.app.domain.logic.MailProviders
import com.budgetpulse.app.domain.logic.SyncInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailProvidersTest {

    @Test
    fun imapHost_isSuggestedFromTheAddress() {
        assertEquals("imap.gmail.com", MailProviders.suggestImapHost("student@gmail.com"))
        assertEquals("imap.mail.yahoo.com", MailProviders.suggestImapHost("a@yahoo.com"))
        assertEquals("imap.help.edu.my", MailProviders.suggestImapHost("me@help.edu.my"))
        assertNull(MailProviders.suggestImapHost("not-an-email"))
    }

    @Test
    fun outlookFamilyIsFlagged() {
        assertTrue(MailProviders.isOutlookFamily("x@outlook.com"))
        assertFalse(MailProviders.isOutlookFamily("x@gmail.com"))
    }

    @Test
    fun emailValidation() {
        assertTrue(MailProviders.isValidEmail(" user@example.com "))
        assertFalse(MailProviders.isValidEmail("user@example"))
        assertFalse(MailProviders.isValidEmail("user example@x.com"))
    }

    @Test
    fun syncInterval_offersTenMinutesAndOneHour_andFallsBackToTheLightOption() {
        assertEquals(listOf(10, 60), SyncInterval.entries.map { it.minutes })
        assertEquals(SyncInterval.TEN_MINUTES, SyncInterval.fromMinutes(10))
        assertEquals(SyncInterval.ONE_HOUR, SyncInterval.fromMinutes(60))
        assertEquals(SyncInterval.ONE_HOUR, SyncInterval.fromMinutes(15))   // an old stored value
        assertEquals(SyncInterval.ONE_HOUR, SyncInterval.DEFAULT)
    }
}

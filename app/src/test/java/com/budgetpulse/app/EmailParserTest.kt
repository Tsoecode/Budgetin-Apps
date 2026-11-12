package com.budgetpulse.app

import com.budgetpulse.app.domain.logic.EmailParser
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.RawEmail
import com.budgetpulse.app.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailParserTest {

    private fun mail(subject: String, body: String, from: String = "notif@bank.example") =
        RawEmail(messageId = "<id@test>", from = from, subject = subject, body = body, receivedAt = 0L)

    @Test
    fun indonesianDebit_isExpense_withMerchantAndCategory() {
        val p = EmailParser.parse(
            mail("Notifikasi Transaksi Debit", "Pembayaran Rp 150.000,00 di Indomaret berhasil. Saldo Rp 2.350.000,00")
        )
        assertNotNull(p)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(150000.0, p.amount, 0.001)
        assertEquals("IDR", p.currencyCode)
        assertEquals("Indomaret", p.description)
        assertEquals(Category.SHOPPING, p.category)
    }

    @Test
    fun indonesianIncomingTransfer_isIncome_salary() {
        val p = EmailParser.parse(
            mail("Dana masuk", "Dana masuk Rp 5.000.000 dari PT Maju Jaya. Gaji bulan Oktober.")
        )
        assertNotNull(p)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals(5_000_000.0, p.amount, 0.001)
        assertEquals(Category.SALARY, p.category)
        assertEquals("PT Maju Jaya", p.description)
    }

    @Test
    fun englishCardPurchase_isExpense_foodCategory() {
        val p = EmailParser.parse(
            mail("Transaction Alert", "Your card ending 1234 was used for a purchase of USD 29.45 at Domino's Pizza on 05 Nov 2026.")
        )
        assertNotNull(p)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(29.45, p.amount, 0.001)
        assertEquals("USD", p.currencyCode)
        assertEquals("Domino's Pizza", p.description)
        assertEquals(Category.FOOD, p.category)
    }

    @Test
    fun englishCredit_isIncome() {
        val p = EmailParser.parse(
            mail("Account update", "Your account has been credited with RM 2,300.50 from ACME SDN BHD.")
        )
        assertNotNull(p)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals(2300.50, p.amount, 0.001)
        assertEquals("MYR", p.currencyCode)
    }

    @Test
    fun creditCardPayment_isNotTreatedAsIncome() {
        val p = EmailParser.parse(
            mail("Payment confirmation", "Credit card payment of RM 1,250.00 has been debited from your account.")
        )
        assertNotNull(p)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(1250.0, p.amount, 0.001)
    }

    @Test
    fun otpAndPromoMails_areIgnored() {
        assertNull(EmailParser.parse(mail("Kode OTP", "Kode OTP Anda 123456 untuk pembayaran Rp 50.000")))
        assertNull(EmailParser.parse(mail("Promo akhir tahun", "Cashback Rp 100.000 untuk pembelian pertama")))
    }

    @Test
    fun balanceOnlyOrDirectionlessMails_areIgnored() {
        assertNull(EmailParser.parse(mail("Info Saldo", "Saldo Rp 1.000.000 per hari ini.")))
        assertNull(EmailParser.parse(mail("Hello", "Our offices open at 9. Total fee Rp 5.000.")))
    }

    @Test
    fun balanceFigureIsNotPickedAsTheAmount() {
        val p = EmailParser.parse(mail("Debit", "Saldo Rp 9.000.000. Pembayaran sebesar Rp 25.000 di Grab."))
        assertNotNull(p)
        assertEquals(25000.0, p!!.amount, 0.001)
        assertEquals(Category.TRANSPORT, p.category)
    }

    @Test
    fun corpSuffixIsNotMistakenForRupiahSymbol() {
        // "Corp 5" must not be read as "Rp 5"
        assertNull(EmailParser.parse(mail("Debit notice", "Acme Corp 5 branches debit")))
    }

    @Test
    fun parseAmount_handlesIndonesianAndEnglishFormats() {
        assertEquals(150000.0, EmailParser.parseAmount("150.000")!!, 0.0)
        assertEquals(1500.0, EmailParser.parseAmount("1,500")!!, 0.0)
        assertEquals(25.5, EmailParser.parseAmount("25.50")!!, 0.0)
        assertEquals(25.5, EmailParser.parseAmount("25,50")!!, 0.0)
        assertEquals(1250000.5, EmailParser.parseAmount("1.250.000,50")!!, 0.0)
        assertEquals(1250000.5, EmailParser.parseAmount("1,250,000.50")!!, 0.0)
        assertEquals(1.5, EmailParser.parseAmount("1.5")!!, 0.0)
        assertNull(EmailParser.parseAmount("abc"))
    }

    @Test
    fun senderFilter_matchesBanksButNotPeopleWithSimilarNames() {
        val keys = EmailParser.DEFAULT_SENDER_KEYWORDS
        assertTrue(EmailParser.isFromBank("BCA <noreply@bca.co.id>", keys))
        assertTrue(EmailParser.isFromBank("Maybank2u <alert@maybank2u.com.my>", keys))
        assertTrue(EmailParser.isFromBank("Notifikasi <info@cimbniaga.co.id>", keys))
        assertFalse(EmailParser.isFromBank("Brian Smith <brian@example.com>", keys))
        assertFalse(EmailParser.isFromBank("Mom <mom@gmail.com>", keys))
    }

    @Test
    fun fallsBackToSubject_whenNoMerchantFound() {
        val p = EmailParser.parse(mail("Debit notification", "Debit Rp 10.000 berhasil."))
        assertNotNull(p)
        assertEquals("Debit notification", p!!.description)
    }
}

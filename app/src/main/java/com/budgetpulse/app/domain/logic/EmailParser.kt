package com.budgetpulse.app.domain.logic

import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.ParsedTransaction
import com.budgetpulse.app.domain.model.RawEmail
import com.budgetpulse.app.domain.model.TxType

/**
 * Turns an m-banking / e-wallet notification e-mail into a transaction.
 *
 * Works on English and Indonesian wording. It is deliberately conservative: an e-mail is only
 * converted when it contains BOTH a recognisable money amount and a clear debit/credit signal,
 * and OTP / promo / statement mails are always ignored.
 */
object EmailParser {

    val DEFAULT_SENDER_KEYWORDS = listOf(
        "bca", "mandiri", "bni", "bri", "cimb", "maybank", "jago", "seabank", "permata", "danamon",
        "ocbc", "hsbc", "uob", "gopay", "ovo", "dana", "shopeepay", "linkaja", "paypal", "wise", "bank",
        "klikbca", "livin", "brimo"
    )

    private val EXCLUDE = listOf(
        "otp", "kode verifikasi", "verification code", "one-time password", "one time password",
        "password", "promo", "newsletter", "e-statement", "estatement", "rekening koran",
        "monthly statement", "lupa pin", "reset pin", "gagal", "failed", "fail"
    )

    // Phrases that decide the direction on their own.
    private val STRONG_INCOME = listOf(
        "payment received", "pembayaran diterima", "dana masuk", "transfer masuk", "incoming transfer",
        "has been credited", "dikreditkan", "credited to your", "menerima transfer", "you received",
        "anda menerima", "salary", "gaji", "payroll", "refund", "pengembalian dana"
    )
    private val STRONG_EXPENSE = listOf(
        "has been debited", "debited from", "didebet", "pembayaran berhasil", "payment successful",
        "successful payment", "tarik tunai", "cash withdrawal", "transfer keluar", "anda telah membayar",
        "purchase", "pembelian", "you paid", "you spent", "dibebankan", "charged",
        "transfer berhasil", "transfer successful"
    )
    private val WEAK_INCOME = listOf("credited", "kredit", "received", "diterima", "deposit", "setoran", "cashback")
    private val WEAK_EXPENSE = listOf("debit", "debited", "payment", "paid", "pembayaran", "withdrawal", "spent", "bayar")

    // These contain "credit"/"debit" but do not indicate direction.
    private val NEUTRALISE = listOf("credit card", "kartu kredit", "credit limit", "limit kredit")

    private val AMOUNT_LABELS = listOf(
        "total bayar", "total pembayaran", "total payment", "total transaksi", "total amount",
        "grand total", "amount paid", "amount payable", "jumlah bayar", "jumlah pembayaran", "nominal pembayaran",
        "amount", "nominal", "jumlah", "sebesar", "senilai", "total", "value"
    )
    private val AMOUNT_BLOCK_CONTEXT = listOf("saldo", "balance", "limit", "available", "tersedia")

    private val CURRENCY_PREFIX =
        Regex("""(?i)(?<![A-Za-z])(us\$|s\$|rp\.?|idr|myr|rm|usd|sgd|eur|gbp|jpy|aud|\$|€|£|₴)\s*([0-9][0-9.,]*[0-9]|[0-9])""")
    private val CURRENCY_SUFFIX = Regex("""(?i)([0-9][0-9.,]*[0-9])\s*(idr|myr|usd|sgd|eur)\b""")

    private val MERCHANT_LABEL = Regex("""(?i)(?:produk|perusahaan|pembayaran|pembayaran ke|warung|toko|penerima|beneficiary|merchant)\s*[:\-]\s*([^\n\r;]{2,40})""")
    private val MERCHANT_PREP = Regex("""(?i)\b(?:at|di|to|ke|from|dari|untuk|kepada)\s+([A-Za-z0-9][A-Za-z0-9 &'\-]{2,40})""")
    private val MERCHANT_CUT = Regex("""(?i)\s+(?:on|pada|dengan|using|via|tanggal|date|ref|reference|with|successfully|berhasil)\b.*""")
    private val MERCHANT_STOP = listOf("your", "account", "rekening", "anda", "the ", "card", "kartu", "a/c", "acc ")

    private val CATEGORY_RULES: List<Pair<Category, List<String>>> = listOf(
        Category.FOOD to listOf("grabfood", "gofood", "shopeefood", "kfc", "mcd", "mcdonald", "starbucks", "restaurant",
            "resto", "cafe", "coffee", "kopi", "food", "pizza", "burger", "bakery", "warung", "makan", "ayam", "nasi", "rice"),
        Category.TRANSPORT to listOf("grab", "gojek", "uber", "bluebird", "transjakarta", "mrt", "krl", "kereta",
            "parking", "parkir", "pertamina", "shell", "bensin", "toll", "e-toll", "petronas", "touch n go"),
        Category.SHOPPING to listOf("indomaret", "alfamart", "tokopedia", "shopee", "lazada", "amazon", "mall",
            "supermarket", "hypermart", "superindo", "giant", "lotus", "aeon", "mydin", "7-eleven", "familymart"),
        Category.BILLS to listOf("pln", "listrik", "pdam", "telkom", "indihome", "internet", "pulsa", "wifi", "bpjs",
            "insurance", "asuransi", "tnb", "unifi", "astro", "utility", "tagihan listrik", "water bill"),
        Category.ENTERTAINMENT to listOf("netflix", "spotify", "steam", "cinema", "bioskop", "xxi", "cgv", "youtube", "disney"),
        Category.HEALTH to listOf("hospital", "rumah sakit", "clinic", "klinik", "pharmacy", "apotek", "kimia farma", "watsons"),
        Category.EDUCATION to listOf("university", "universitas", "tuition", "school", "sekolah", "udemy", "coursera", "kuliah"),
        Category.TRAVEL to listOf("airasia", "garuda", "lion air", "traveloka", "tiket.com", "agoda", "booking.com", "hotel", "airlines")
    )

    /** True when the sender address / display name contains one of the configured keywords. */
    fun isFromBank(from: String, keywords: List<String>): Boolean {
        val tokens = from.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val keys = keywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        // Short keywords ("bri") must equal a whole token so that "Brian" does not match;
        // longer ones may be a prefix/part of a token ("cimbniaga", "bankmandiri").
        return keys.any { k ->
            tokens.any { t -> t == k || (k.length >= 4 && t.startsWith(k)) || (k.length >= 5 && t.contains(k)) }
        }
    }

    fun parse(email: RawEmail): ParsedTransaction? {
        val subject = email.subject.trim()
        val rawText = (subject + "\n" + email.body).replace('\u00A0', ' ')
        val lower = rawText.lowercase()

        if (EXCLUDE.any { lower.contains(it) }) return null

        val type = detectType(lower) ?: return null
        val (amount, currency) = extractAmount(rawText) ?: return null
        if (amount <= 0.0) return null

        val merchant = extractMerchant(rawText)
        val description = (merchant ?: subject).replace(Regex("\\s+"), " ").trim().split(" ").take(5).joinToString(" ") + "..."
        val category = guessCategory(type, (merchant ?: "") + " " + subject + " " + email.body.take(300))
        return ParsedTransaction(type, amount, currency, description, category)
    }

    // ---------- direction ----------
    private fun detectType(lowerRaw: String): TxType? {
        var text = lowerRaw
        NEUTRALISE.forEach { text = text.replace(it, " ") }

        val strongIn = firstIndex(text, STRONG_INCOME)
        val strongOut = firstIndex(text, STRONG_EXPENSE)
        if (strongIn >= 0 && strongOut < 0) return TxType.INCOME
        if (strongOut >= 0 && strongIn < 0) return TxType.EXPENSE
        if (strongIn >= 0 && strongOut >= 0) return if (strongIn < strongOut) TxType.INCOME else TxType.EXPENSE

        val weakIn = firstIndex(text, WEAK_INCOME)
        val weakOut = firstIndex(text, WEAK_EXPENSE)
        return when {
            weakIn >= 0 && weakOut < 0 -> TxType.INCOME
            weakOut >= 0 && weakIn < 0 -> TxType.EXPENSE
            weakIn >= 0 && weakOut >= 0 -> if (weakIn < weakOut) TxType.INCOME else TxType.EXPENSE
            else -> null
        }
    }

    private fun firstIndex(text: String, words: List<String>): Int =
        words.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1

    // ---------- amount ----------
    private data class AmountHit(val start: Int, val value: Double, val currency: String?)

    private fun extractAmount(text: String): Pair<Double, String?>? {
        val hits = mutableListOf<AmountHit>()
        CURRENCY_PREFIX.findAll(text).forEach { m ->
            parseAmount(m.groupValues[2])?.let { hits += AmountHit(m.range.first, it, normaliseCurrency(m.groupValues[1])) }
        }
        CURRENCY_SUFFIX.findAll(text).forEach { m ->
            parseAmount(m.groupValues[1])?.let { hits += AmountHit(m.range.first, it, normaliseCurrency(m.groupValues[2])) }
        }
        if (hits.isEmpty()) return null

        val lower = text.lowercase()
        val usable = hits.filter { h ->
            val ctx = lower.substring(maxOf(0, h.start - 25), h.start)
            AMOUNT_BLOCK_CONTEXT.none { ctx.contains(it) }
        }.sortedBy { it.start }
        if (usable.isEmpty()) return null

        val labelled = AMOUNT_LABELS.firstNotNullOfOrNull { label ->
            usable.firstOrNull { h ->
                val ctx = lower.substring(maxOf(0, h.start - 50), h.start)
                ctx.contains(label)
            }
        }
        val chosen = labelled ?: usable.first()
        return chosen.value to chosen.currency
    }

    private fun normaliseCurrency(raw: String): String? = when (raw.lowercase().trimEnd('.')) {
        "rp", "idr" -> "IDR"
        "rm", "myr" -> "MYR"
        "us$", "$", "usd" -> "USD"
        "s$", "sgd" -> "SGD"
        "€", "eur" -> "EUR"
        "£", "gbp" -> "GBP"
        "jpy" -> "JPY"
        "aud" -> "AUD"
        "₴" -> "UAH"
        else -> null
    }

    /**
     * Handles both "1.250.000,50" (Indonesian) and "1,250,000.50" (English).
     * A single separator followed by exactly three digits is treated as a thousands separator.
     */
    fun parseAmount(raw: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val normalised = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimalIsComma = lastComma > lastDot
                if (decimalIsComma) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            }
            lastDot >= 0 || lastComma >= 0 -> {
                val sep = if (lastDot >= 0) '.' else ','
                val count = s.count { it == sep }
                val digitsAfter = s.length - s.lastIndexOf(sep) - 1
                if (count > 1 || digitsAfter == 3) s.replace(sep.toString(), "") else s.replace(sep, '.')
            }
            else -> s
        }
        return normalised.toDoubleOrNull()
    }

    // ---------- merchant / category ----------
    private fun extractMerchant(text: String): String? {
        MERCHANT_LABEL.find(text)?.let { return clean(it.groupValues[1]) }
        for (m in MERCHANT_PREP.findAll(text)) {
            val candidate = clean(m.groupValues[1]) ?: continue
            val lc = candidate.lowercase()
            if (MERCHANT_STOP.any { lc.startsWith(it) }) continue
            if (candidate.length < 3) continue
            return candidate
        }
        return null
    }

    private fun clean(raw: String): String? {
        val cut = raw.replace(MERCHANT_CUT, "").trim().trim('.', ',', '-', ' ')
        return cut.takeIf { it.length >= 2 }
    }

    private fun guessCategory(type: TxType, context: String): Category {
        val c = context.lowercase()
        if (type == TxType.INCOME) {
            return when {
                containsKeyword(c, "bonus") -> Category.BONUS
                containsKeyword(c, "salary") || containsKeyword(c, "gaji") || containsKeyword(c, "payroll") -> Category.SALARY
                else -> Category.OTHER_INCOME
            }
        }
        for ((cat, keys) in CATEGORY_RULES) {
            if (keys.any { containsKeyword(c, it) }) return cat
        }
        return Category.OTHER_EXPENSE
    }

    /** Keyword must start at a word boundary ("mall" must not match "small"); short ones must be whole words. */
    private fun containsKeyword(text: String, kw: String): Boolean {
        val escaped = Regex.escape(kw)
        val pattern = if (kw.length <= 3) "\\b" + escaped + "\\b" else "\\b" + escaped
        return Regex(pattern).containsMatchIn(text)
    }
}

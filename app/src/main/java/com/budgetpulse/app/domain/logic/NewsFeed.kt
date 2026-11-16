package com.budgetpulse.app.domain.logic

import com.budgetpulse.app.domain.model.NewsItem
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import javax.xml.parsers.DocumentBuilderFactory

/** Decides WHICH Google News query to run and WHEN the cached news is considered "this week's". */
object NewsPlanner {
    /** The topic rotates every ISO week so the carousel visibly changes weekly. */
    val TOPICS = listOf(
        "personal finance savings",
        "budgeting tips",
        "inflation interest rates",
        "currency exchange rupiah ringgit dollar",
        "investing for beginners"
    )

    fun weekKey(date: LocalDate): String {
        val wf = WeekFields.ISO
        return "%d-W%02d".format(date.get(wf.weekBasedYear()), date.get(wf.weekOfWeekBasedYear()))
    }

    fun topicFor(date: LocalDate): String =
        TOPICS[date.get(WeekFields.ISO.weekOfWeekBasedYear()) % TOPICS.size]

    /** Google News RSS search limited to the last 7 days. */
    fun feedUrl(date: LocalDate, hl: String = "en-ID", gl: String = "ID", ceid: String = "ID:en"): String {
        val q = URLEncoder.encode(topicFor(date) + " when:7d", "UTF-8")
        return "https://news.google.com/rss/search?q=$q&hl=$hl&gl=$gl&ceid=$ceid"
    }
}

object NewsFeedParser {

    fun parse(xml: String, max: Int = 10): List<NewsItem> {
        val factory = DocumentBuilderFactory.newInstance()
        // Harden against XXE; some Android parsers do not support every feature, so ignore failures.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.isExpandEntityReferences = false }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val nodes = doc.getElementsByTagName("item")
        val seen = HashSet<String>()
        val out = ArrayList<NewsItem>()
        for (i in 0 until nodes.length) {
            val item = nodes.item(i) as? Element ?: continue
            val rawTitle = item.text("title") ?: continue
            val link = item.text("link") ?: continue
            val sourceElement = item.element("source")
            val source = sourceElement?.textContent?.trim().orEmpty().ifEmpty { "News" }
            val sourceUrl = sourceElement?.getAttribute("url")?.trim().orEmpty()
            val title = rawTitle.removeSuffix(" - $source").trim()
            if (title.isEmpty() || !seen.add(title.lowercase())) continue
            val published = item.text("pubDate")?.let { parseDate(it) } ?: 0L
            val host = runCatching { java.net.URI(sourceUrl).host?.removePrefix("www.") }.getOrNull()
            val imageUrl = host?.let(FINANCE_BANNERS_BY_SOURCE::get) ?: bannerFor(title)
            out += NewsItem(title, source, link, published, imageUrl, sourceUrl)
            if (out.size >= max) break
        }
        return out
    }

    private val FINANCE_BANNERS = listOf(
        "https://images.unsplash.com/photo-1579621970563-ebec7560ff3e?w=800&auto=format&fit=crop&q=80",
        "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800&auto=format&fit=crop&q=80",
        "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800&auto=format&fit=crop&q=80",
        "https://images.unsplash.com/photo-1553729459-efe14ef6055d?w=800&auto=format&fit=crop&q=80",
        "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=800&auto=format&fit=crop&q=80",
        "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?w=800&auto=format&fit=crop&q=80",
        "https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?w=800&auto=format&fit=crop&q=80",
        "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?w=800&auto=format&fit=crop&q=80"
    )

    private val SOURCE_LOGOS = mapOf(
        "forbes.com" to "https://i.forbesimg.com/media/assets/forbes_1200x1200.jpg",
        "cnbc.com" to "https://upload.wikimedia.org/wikipedia/commons/e/e3/CNBC_logo.svg",
        "reuters.com" to "https://upload.wikimedia.org/wikipedia/commons/8/8d/Reuters_Logo.svg",
        "bbc.com" to "https://upload.wikimedia.org/wikipedia/commons/4/41/BBC_Logo_2021.svg",
        "bbc.co.uk" to "https://upload.wikimedia.org/wikipedia/commons/4/41/BBC_Logo_2021.svg",
        "finance.yahoo.com" to "https://s.yimg.com/rz/p/yahoo_finance_en-US_h_p_finance_2.png",
        "nerdwallet.com" to "https://www.nerdwallet.com/favicon.ico",
        "thejakartapost.com" to "https://www.thejakartapost.com/resources/images/logo.svg",
        "kompas.com" to "https://asset.kompas.com/data/2017/wp/images/logo-kompascom.svg"
    )
    private const val LOGO_FALLBACK = "https://www.google.com/s2/favicons?domain="
    private const val LOGO_SIZE = "&sz=128"

    private val FINANCE_BANNERS_BY_SOURCE = mapOf(
        "delawaregazette.com" to "https://images.unsplash.com/photo-1579621970563-ebec7560ff3e?w=800&auto=format&fit=crop&q=80",
        "kbtx.com" to "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=800&auto=format&fit=crop&q=80"
    )

    /** Deterministic backdrop photo (same article always gets the same picture) on a high-speed CDN. */
    fun bannerFor(title: String): String {
        val index = Math.abs(title.hashCode()) % FINANCE_BANNERS.size
        return FINANCE_BANNERS[index]
    }

    /**
     * The outlet's real logo, taken from the feed's own `<source url>` and served from Google's icon CDN.
     * Returns null when the item has no publisher homepage, so callers can hide the badge entirely.
     */
    fun logoFor(sourceUrl: String): String? {
        val host = runCatching { java.net.URI(sourceUrl.trim()).host }.getOrNull()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() && it.contains('.') }
            ?: return null
        return SOURCE_LOGOS[host] ?: "$LOGO_FALLBACK$host$LOGO_SIZE"
    }

    private fun parseDate(s: String): Long = runCatching {
        ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun Element.text(tag: String): String? = element(tag)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun Element.element(tag: String): Element? {
        val list = getElementsByTagName(tag)
        for (i in 0 until list.length) {
            val node = list.item(i)
            if (node is Element) return node
        }
        return null
    }
}

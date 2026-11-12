package com.budgetpulse.app.domain.logic

import com.budgetpulse.app.domain.model.NewsItem
import org.w3c.dom.Element
import org.w3c.dom.Node
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
            val source = item.text("source") ?: "News"
            val title = rawTitle.removeSuffix(" - $source").trim()
            if (title.isEmpty() || !seen.add(title.lowercase())) continue
            val published = item.text("pubDate")?.let { parseDate(it) } ?: 0L
            out += NewsItem(title, source, link, published, bannerFor(title))
            if (out.size >= max) break
        }
        return out
    }

    /** Deterministic decorative banner (same article always gets the same picture). */
    fun bannerFor(title: String): String =
        "https://picsum.photos/seed/bp${Math.abs(title.hashCode()) % 500}/640/360"

    private fun parseDate(s: String): Long = runCatching {
        ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun Element.text(tag: String): String? {
        val list = getElementsByTagName(tag)
        if (list.length == 0) return null
        val node = list.item(0)
        return if (node.nodeType == Node.ELEMENT_NODE) node.textContent?.trim()?.takeIf { it.isNotEmpty() } else null
    }
}

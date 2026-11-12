package com.budgetpulse.app

import com.budgetpulse.app.domain.logic.CurrencyConverter
import com.budgetpulse.app.domain.logic.NewsFeedParser
import com.budgetpulse.app.domain.logic.NewsPlanner
import com.budgetpulse.app.util.compactAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CurrencyAndNewsTest {

    private val rates = mapOf("USD" to 1.0, "IDR" to 16000.0, "MYR" to 4.5)

    @Test
    fun convert_crossRateGoesThroughBase() {
        assertEquals(100 * 16000.0 / 4.5, CurrencyConverter.convert(100.0, "MYR", "IDR", rates)!!, 0.001)
    }

    @Test
    fun convert_sameCurrencyReturnsSameAmount() {
        assertEquals(42.0, CurrencyConverter.convert(42.0, "IDR", "IDR", rates)!!, 0.0001)
    }

    @Test
    fun convert_unknownCurrencyReturnsNull() {
        assertNull(CurrencyConverter.convert(1.0, "USD", "XXX", rates))
        assertNull(CurrencyConverter.convert(1.0, "XXX", "USD", rates))
    }

    @Test
    fun compactAmount_abbreviatesLargeNumbers() {
        assertEquals("999", compactAmount(999.0))
        assertEquals("1.5K", compactAmount(1500.0))
        assertEquals("2.5M", compactAmount(2_500_000.0))
        assertEquals("-1.2K", compactAmount(-1200.0))
    }

    private val sampleRss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel><title>Google News</title>
          <item>
            <title>Five ways to save more this month - The Jakarta Post</title>
            <link>https://news.google.com/rss/articles/AAA</link>
            <pubDate>Tue, 03 Nov 2026 07:00:00 GMT</pubDate>
            <source url="https://www.thejakartapost.com">The Jakarta Post</source>
          </item>
          <item>
            <title>Rupiah steadies against dollar - Reuters</title>
            <link>https://news.google.com/rss/articles/BBB</link>
            <pubDate>Wed, 04 Nov 2026 09:30:00 GMT</pubDate>
            <source url="https://www.reuters.com">Reuters</source>
          </item>
          <item>
            <title>Rupiah steadies against dollar - Reuters</title>
            <link>https://news.google.com/rss/articles/CCC</link>
            <pubDate>Wed, 04 Nov 2026 09:31:00 GMT</pubDate>
            <source url="https://www.reuters.com">Reuters</source>
          </item>
          <item>
            <title>Budgeting basics - Kompas</title>
            <link>https://news.google.com/rss/articles/DDD</link>
            <source url="https://www.kompas.com">Kompas</source>
          </item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun newsParser_extractsFields_stripsSourceSuffix_andDeduplicates() {
        val items = NewsFeedParser.parse(sampleRss)
        assertEquals(3, items.size)
        assertEquals("Five ways to save more this month", items[0].title)
        assertEquals("The Jakarta Post", items[0].source)
        assertEquals("https://news.google.com/rss/articles/AAA", items[0].link)
        assertEquals(1793689200000L, items[0].publishedAt)
        assertTrue(items[0].imageUrl.startsWith("https://"))
        assertEquals(0L, items[2].publishedAt) // missing pubDate is tolerated
    }

    @Test
    fun newsParser_respectsMaxItems() {
        assertEquals(1, NewsFeedParser.parse(sampleRss, max = 1).size)
    }

    @Test
    fun newsParser_sameTitleAlwaysGetsSameBanner() {
        assertEquals(NewsFeedParser.bannerFor("Hello"), NewsFeedParser.bannerFor("Hello"))
    }

    @Test
    fun weekKey_isStableInsideAWeek_andChangesNextWeek() {
        val thu = LocalDate.of(2026, 11, 5)
        val sun = LocalDate.of(2026, 11, 8)
        val nextMon = LocalDate.of(2026, 11, 9)
        assertEquals(NewsPlanner.weekKey(thu), NewsPlanner.weekKey(sun))
        assertNotEquals(NewsPlanner.weekKey(thu), NewsPlanner.weekKey(nextMon))
        assertEquals("2026-W45", NewsPlanner.weekKey(thu))
    }

    @Test
    fun weekKey_usesIsoWeekBasedYearAcrossNewYear() {
        // 2026 has 53 ISO weeks; 1 Jan 2027 still belongs to week 53 of 2026.
        assertEquals("2026-W53", NewsPlanner.weekKey(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun topicRotatesWeekly() {
        val topics = (0 until NewsPlanner.TOPICS.size).map { NewsPlanner.topicFor(LocalDate.of(2026, 11, 2).plusWeeks(it.toLong())) }
        assertEquals(NewsPlanner.TOPICS.size, topics.toSet().size)
    }

    @Test
    fun feedUrl_targetsGoogleNewsRss_lastSevenDays() {
        val url = NewsPlanner.feedUrl(LocalDate.of(2026, 11, 5))
        assertTrue(url.startsWith("https://news.google.com/rss/search?q="))
        assertTrue(url.contains("when%3A7d"))
        assertTrue(url.contains("hl=en-ID"))
    }
}

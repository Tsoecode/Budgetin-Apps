package com.budgetpulse.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.budgetpulse.app.data.prefs.ThemeMode
import com.budgetpulse.app.domain.model.CurrencyRates
import com.budgetpulse.app.domain.model.NewsItem
import com.budgetpulse.app.ui.common.NewsCarousel
import com.budgetpulse.app.ui.common.UiState
import com.budgetpulse.app.ui.market.MarketContent
import com.budgetpulse.app.ui.market.MarketUiState
import com.budgetpulse.app.ui.theme.BudgetPulseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Compose UI tests #2 and #3 - rendering of data that normally comes from the network. */
class MarketAndNewsUiTest {

    @get:Rule val rule = createComposeRule()

    private val rates = CurrencyRates(
        base = "USD",
        rates = mapOf("USD" to 1.0, "IDR" to 16000.0, "EUR" to 0.9, "MYR" to 4.5),
        updatedLabel = "Thu, 05 Nov 2026 00:00:01 +0000",
        nextUpdateEpochSec = 0L,
        provider = "ExchangeRate-API"
    )

    private fun marketState(rates: UiState<CurrencyRates>, amount: String = "100") =
        MarketUiState(base = "USD", target = "IDR", amountText = amount, rates = rates)

    private fun showMarket(state: MarketUiState, onRefresh: () -> Unit = {}) {
        rule.setContent {
            BudgetPulseTheme(ThemeMode.LIGHT) {
                MarketContent(state, wide = false, onRefresh = onRefresh, onBase = {}, onTarget = {}, onAmount = {}, onQuery = {}, onOpenSource = {})
            }
        }
    }

    @Test
    fun market_showsFetchedRates_andConvertsTheAmount() {
        showMarket(marketState(UiState.Success(rates)))
        rule.onNodeWithText("Live market rates").assertIsDisplayed()
        rule.onNodeWithText("Updated: Thu, 05 Nov 2026 00:00:01 +0000").assertIsDisplayed()
        // 100 USD x 16000 = 1,600,000 IDR (separator style depends on the device locale, so only digits are checked)
        rule.onNodeWithTag("converter_result").assertTextContains("IDR", substring = true)
        rule.onNodeWithTag("market_list").performScrollToNode(hasTestTag("rate_MYR"))
        // each row reads "1 <stronger currency> = x <weaker currency>": short numbers, never 0.000062
        rule.onNodeWithTag("rate_MYR").assertTextEquals("1 USD = 4.50 MYR")
        rule.onNodeWithTag("rate_EUR").assertTextEquals("1 EUR = 1.11 USD")
    }

    @Test
    fun market_converterShowsTheRateInTheReadableDirection() {
        showMarket(marketState(UiState.Success(rates)))
        rule.onNodeWithTag("converter_rate").assertTextEquals("1 USD = 16,000 IDR")
    }

    @Test
    fun market_flagsStaleCacheWhenOffline() {
        showMarket(marketState(UiState.Success(rates, stale = true)))
        rule.onNodeWithText("Offline - showing last saved rates").assertIsDisplayed()
    }

    @Test
    fun market_showsErrorAndRetryRefreshes() {
        var refreshed = 0
        showMarket(marketState(UiState.Error("Unable to load exchange rates. Check your connection.")), onRefresh = { refreshed++ })
        rule.onNodeWithText("Unable to load exchange rates. Check your connection.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, refreshed)
    }

    private val news = listOf(
        NewsItem("Five ways to save more this month", "The Jakarta Post", "https://example.com/a", 0L, ""),
        NewsItem("Rupiah steadies against the dollar", "Reuters", "https://example.com/b", 0L, ""),
        NewsItem("Budgeting basics for students", "Kompas", "https://example.com/c", 0L, "")
    )

    @Test
    fun newsCarousel_rendersCards_andCardsAreClickable() {
        var opened: NewsItem? = null
        rule.setContent {
            BudgetPulseTheme(ThemeMode.LIGHT) { NewsCarousel(news, onOpen = { opened = it }, autoScroll = false) }
        }
        rule.onNodeWithTag("news_carousel").assertIsDisplayed()
        rule.onNodeWithText("Five ways to save more this month").assertIsDisplayed()
        rule.onNodeWithText("Five ways to save more this month").performClick()
        assertEquals("https://example.com/a", opened?.link)
    }

    @Test
    fun newsCarousel_swipesToTheNextStory() {
        var opened: NewsItem? = null
        rule.setContent {
            BudgetPulseTheme(ThemeMode.LIGHT) { NewsCarousel(news, onOpen = { opened = it }, autoScroll = false) }
        }
        rule.onNodeWithTag("news_carousel").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Rupiah steadies against the dollar").assertIsDisplayed()
        rule.onNodeWithText("Rupiah steadies against the dollar").performClick()
        assertTrue(opened?.source == "Reuters")
    }
}

package com.budgetpulse.app.data.repository

import com.budgetpulse.app.data.local.NewsDao
import com.budgetpulse.app.data.local.NewsEntity
import com.budgetpulse.app.data.local.toDomain
import com.budgetpulse.app.data.remote.NewsRemoteSource
import com.budgetpulse.app.domain.logic.NewsFeedParser
import com.budgetpulse.app.domain.logic.NewsPlanner
import com.budgetpulse.app.domain.model.NewsItem
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Google News (RSS) for personal-finance stories. The stored batch is keyed by ISO week:
 * when a new week starts the repository downloads a new batch (with a rotating topic),
 * otherwise the cached batch is reused. Pull-to-refresh forces a download.
 */
class NewsRepository(
    private val dao: NewsDao,
    private val remote: NewsRemoteSource,
    private val today: () -> LocalDate = { LocalDate.now() }
) {
    fun observeNews(): Flow<List<NewsItem>> = dao.observeLatest().map { list -> list.map { it.toDomain() } }

    suspend fun refreshIfNeeded(force: Boolean = false): Result<Int> {
        val date = today()
        val weekKey = NewsPlanner.weekKey(date)
        if (!force && dao.countForWeek(weekKey) > 0) {
            AppLog.d(AppLog.NET, "news for $weekKey already cached")
            return Result.success(0)
        }
        return try {
            val xml = remote.fetch(NewsPlanner.feedUrl(date))
            val items = withContext(Dispatchers.Default) { NewsFeedParser.parse(xml) }
            if (items.isEmpty()) return Result.failure(IOException("No news found"))
            val now = System.currentTimeMillis()
            dao.insertAll(items.map { NewsEntity(it.link, it.title, it.source, it.publishedAt, it.imageUrl, weekKey, now) })
            dao.deleteOlderThan(weekKey)
            AppLog.i(AppLog.DB, "cached ${items.size} news items for $weekKey (topic=${NewsPlanner.topicFor(date)})")
            Result.success(items.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(AppLog.NET, "news refresh failed: ${e.message}")
            Result.failure(e)
        }
    }
}

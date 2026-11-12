package com.budgetpulse.app.data.remote

import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException

/** ExchangeRate-API "open access" endpoint (no key, daily rates, attribution required). */
@Serializable
data class ErApiResponse(
    val result: String = "",
    @SerialName("time_last_update_utc") val timeLastUpdateUtc: String = "",
    @SerialName("time_next_update_unix") val timeNextUpdateUnix: Long = 0,
    @SerialName("base_code") val baseCode: String = "",
    val rates: Map<String, Double> = emptyMap()
)

interface ExchangeRateApi {
    @GET("v6/latest/{base}")
    suspend fun latest(@Path("base") base: String): ErApiResponse
}

/** Frankfurter = free mirror of the European Central Bank reference rates (used as fallback). */
@Serializable
data class FrankfurterResponse(
    val base: String = "",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap()
)

interface FrankfurterApi {
    @GET("v1/latest")
    suspend fun latest(@Query("base") base: String): FrankfurterResponse
}

/** Logs every request/response (method, URL, status, duration) - never bodies or headers. */
class NetworkLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start = System.nanoTime()
        AppLog.d(AppLog.NET, "--> ${request.method} ${request.url}")
        try {
            val response = chain.proceed(request)
            val ms = (System.nanoTime() - start) / 1_000_000
            AppLog.i(AppLog.NET, "<-- ${response.code} ${request.url} (${ms}ms)")
            return response
        } catch (e: IOException) {
            AppLog.e(AppLog.NET, "xx  ${request.url} failed: ${e.message}", e)
            throw e
        }
    }
}

/** Plain HTTP fetch for the Google News RSS feed. Runs on Dispatchers.IO, never on the main thread. */
class NewsRemoteSource(private val client: OkHttpClient) {
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) Budgetin/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response body")
        }
    }
}

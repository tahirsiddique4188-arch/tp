package com.titan.ide.network

import android.util.Log
import com.example.data.model.GroundingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * TitanWebSearchGrounding — Zero-Key, Zero-CAPTCHA Live Web & News Grounding
 * 1. Google News RSS (https://news.google.com/rss/search) — fresh 2026 breaking news in ~250ms
 * 2. Bing Search RSS (https://www.bing.com/search?format=rss) — general live web facts
 * 3. DuckDuckGo HTML fallback
 * 4. Wikipedia API fallback
 */
object TitanWebSearchGrounding {

    private const val TAG = "TitanWebGrounding"
    private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun search(query: String, maxResults: Int = 4): List<GroundingSource> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val combined = mutableListOf<GroundingSource>()

        // 1. Bing Search RSS (works cleanly on mobile IPs with zero CAPTCHA)
        try {
            combined.addAll(searchBingRss(cleanQuery, maxResults))
        } catch (e: Exception) {
            Log.w(TAG, "Bing RSS notice: ${e.message}")
        }

        // 2. Google News RSS (best for latest/breaking news, prices, tech, sports)
        if (combined.size < maxResults) {
            try {
                val news = searchGoogleNewsRss(cleanQuery, maxResults - combined.size)
                combined.addAll(news)
            } catch (e: Exception) {
                Log.w(TAG, "Google News RSS notice: ${e.message}")
            }
        }

        if (combined.isNotEmpty()) {
            return@withContext combined.distinctBy { it.title }.take(maxResults)
        }

        // 3. DuckDuckGo HTML fallback
        try {
            val ddgResults = searchDuckDuckGoHtml(cleanQuery, maxResults)
            if (ddgResults.isNotEmpty()) return@withContext ddgResults
        } catch (e: Exception) {
            Log.w(TAG, "DDG HTML failed: ${e.message}")
        }

        // 4. Wikipedia API fallback
        try {
            val wikiResults = searchWikipedia(cleanQuery, maxResults)
            if (wikiResults.isNotEmpty()) return@withContext wikiResults
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search failed: ${e.message}")
        }

        emptyList()
    }

    private fun searchBingRss(query: String, maxResults: Int): List<GroundingSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.bing.com/search?q=$encoded&format=rss&mkt=en-IN"
        return parseRssFeed(url, maxResults)
    }

    private fun searchGoogleNewsRss(query: String, maxResults: Int): List<GroundingSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://news.google.com/rss/search?q=$encoded&hl=en-IN&gl=IN&ceid=IN:en"
        return parseRssFeed(url, maxResults)
    }

    private fun parseRssFeed(url: String, maxResults: Int): List<GroundingSource> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
            .get()
            .build()

        val results = mutableListOf<GroundingSource>()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val xml = resp.body?.string().orEmpty()
            val itemPattern = Pattern.compile("<item>([\\s\\S]*?)</item>", Pattern.CASE_INSENSITIVE)
            val titlePattern = Pattern.compile("<title>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?</title>", Pattern.CASE_INSENSITIVE)
            val linkPattern = Pattern.compile("<link>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?</link>", Pattern.CASE_INSENSITIVE)
            val descPattern = Pattern.compile("<description>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?</description>", Pattern.CASE_INSENSITIVE)
            val datePattern = Pattern.compile("<pubDate>([\\s\\S]*?)</pubDate>", Pattern.CASE_INSENSITIVE)

            val im = itemPattern.matcher(xml)
            while (im.find() && results.size < maxResults) {
                val block = im.group(1) ?: continue
                val tm = titlePattern.matcher(block)
                val lm = linkPattern.matcher(block)
                val dm = descPattern.matcher(block)
                val pm = datePattern.matcher(block)

                val title = if (tm.find()) cleanHtml(tm.group(1) ?: "") else ""
                val link = if (lm.find()) (lm.group(1) ?: "").trim() else ""
                val desc = if (dm.find()) cleanHtml(dm.group(1) ?: "") else ""
                val pubDate = if (pm.find()) (pm.group(1) ?: "").trim() else ""
                val fullSnippet = if (pubDate.isNotBlank()) "[$pubDate] $desc" else desc

                if (title.isNotBlank() && link.startsWith("http")) {
                    results.add(GroundingSource(title = title, url = link, snippet = fullSnippet.take(260)))
                }
            }
        }
        return results
    }

    private fun searchDuckDuckGoHtml(query: String, maxResults: Int): List<GroundingSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,bn;q=0.8")
            .build()

        val results = mutableListOf<GroundingSource>()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val html = resp.body?.string().orEmpty()

            val snippetPattern = Pattern.compile(
                """<a[^>]*class="result__snippet[^"]*"[^>]*>(.*?)</a>""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val titlePattern = Pattern.compile(
                """<a[^>]*class="result__url[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val fullResultPattern = Pattern.compile(
                """<div[^>]*class="result__body[^"]*"[^>]*>([\s\S]*?)</div>\s*</div>""",
                Pattern.CASE_INSENSITIVE
            )

            val matcher = fullResultPattern.matcher(html)
            while (matcher.find() && results.size < maxResults) {
                val block = matcher.group(1) ?: continue
                val sm = snippetPattern.matcher(block)
                val snippet = if (sm.find()) cleanHtml(sm.group(1) ?: "") else ""

                val tm = titlePattern.matcher(block)
                var link = ""
                var title = ""
                if (tm.find()) {
                    link = tm.group(1) ?: ""
                    title = cleanHtml(tm.group(2) ?: "")
                }

                if (link.contains("uddg=")) {
                    val raw = link.substringAfter("uddg=").substringBefore("&")
                    link = try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { link }
                }

                if (title.isNotBlank() && link.startsWith("http")) {
                    results.add(GroundingSource(title = title, url = link, snippet = snippet.take(240)))
                }
            }
        }
        return results
    }

    private fun searchWikipedia(query: String, maxResults: Int): List<GroundingSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1"
        val req = Request.Builder().url(url).header("User-Agent", DESKTOP_UA).get().build()

        val results = mutableListOf<GroundingSource>()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val searchArr = json.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()

            for (i in 0 until searchArr.length().coerceAtMost(maxResults)) {
                val item = searchArr.optJSONObject(i) ?: continue
                val title = item.optString("title", "")
                val snippet = cleanHtml(item.optString("snippet", ""))
                val articleUrl = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                if (title.isNotBlank()) {
                    results.add(GroundingSource(title = title, url = articleUrl, snippet = snippet.take(240)))
                }
            }
        }
        return results
    }

    fun buildGroundingPromptBlock(sources: List<GroundingSource>): String {
        if (sources.isEmpty()) return ""
        return buildString {
            append("[LIVE REAL-TIME WEB SEARCH RESULTS — USE THESE FRESH FACTS DIRECTLY AND DO NOT SAY YOU LACK LIVE INTERNET SEARCH]:\n")
            sources.forEachIndexed { i, s ->
                append("${i + 1}. Title: ${s.title}\n")
                append("   URL: ${s.url}\n")
                if (!s.snippet.isNullOrBlank()) {
                    append("   Snippet: ${s.snippet}\n")
                }
            }
            append("Synthesize the live web results above directly into your answer in the user's language.\n")
        }
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

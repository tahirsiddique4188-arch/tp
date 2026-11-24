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
 * TitanWebSearchGrounding — Zero-Key Web Search & Live Knowledge Grounding
 * Reverse-engineered from Bypass IDE's WebSearchService:
 *  1. Scrapes https://html.duckduckgo.com/html/?q= directly (zero API key needed!).
 *  2. Fallback to DuckDuckGo Lite (https://lite.duckduckgo.com/lite/).
 *  3. Fallback to Wikipedia API (https://en.wikipedia.org/w/api.php?action=query&list=search).
 *  4. Produces GroundingSource items with clickable URLs and snippets for Titan UI.
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

        // 1. Try DuckDuckGo HTML
        try {
            val ddgResults = searchDuckDuckGoHtml(cleanQuery, maxResults)
            if (ddgResults.isNotEmpty()) return@withContext ddgResults
        } catch (e: Exception) {
            Log.w(TAG, "DDG HTML failed: ${e.message}")
        }

        // 2. Try DuckDuckGo Lite
        try {
            val liteResults = searchDuckDuckGoLite(cleanQuery, maxResults)
            if (liteResults.isNotEmpty()) return@withContext liteResults
        } catch (e: Exception) {
            Log.w(TAG, "DDG Lite failed: ${e.message}")
        }

        // 3. Fallback to Wikipedia API
        try {
            val wikiResults = searchWikipedia(cleanQuery, maxResults)
            if (wikiResults.isNotEmpty()) return@withContext wikiResults
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search failed: ${e.message}")
        }

        emptyList()
    }

    private fun searchDuckDuckGoHtml(query: String, maxResults: Int): List<GroundingSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
            .build()

        val results = mutableListOf<GroundingSource>()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val html = resp.body?.string().orEmpty()

            // Extract results via regex
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
                    link = try { URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { link }
                }

                if (title.isNotBlank() && link.startsWith("http")) {
                    results.add(GroundingSource(title = title, url = link, snippet = snippet.take(240)))
                }
            }
        }
        return results
    }

    private fun searchDuckDuckGoLite(query: String, maxResults: Int): List<GroundingSource> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://lite.duckduckgo.com/lite/"
        val body = okhttp3.FormBody.Builder().add("q", query).build()
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val results = mutableListOf<GroundingSource>()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val html = resp.body?.string().orEmpty()

            val linkPattern = Pattern.compile("""<a[^>]*class="result-link"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", Pattern.CASE_INSENSITIVE)
            val snippetPattern = Pattern.compile("""<td[^>]*class="result-snippet"[^>]*>(.*?)</td>""", Pattern.CASE_INSENSITIVE)

            val lm = linkPattern.matcher(html)
            val sm = snippetPattern.matcher(html)

            while (lm.find() && results.size < maxResults) {
                var href = lm.group(1) ?: ""
                val title = cleanHtml(lm.group(2) ?: "")
                val snip = if (sm.find()) cleanHtml(sm.group(1) ?: "") else ""

                if (href.contains("uddg=")) {
                    val raw = href.substringAfter("uddg=").substringBefore("&")
                    href = try { URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { href }
                }
                if (title.isNotBlank() && href.startsWith("http")) {
                    results.add(GroundingSource(title = title, url = href, snippet = snip.take(240)))
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
            append("[LIVE WEB GROUNDING RESULTS FROM FREE SEARCH]:\n")
            sources.forEachIndexed { i, s ->
                append("${i + 1}. Title: ${s.title}\n")
                append("   URL: ${s.url}\n")
                if (!s.snippet.isNullOrBlank()) {
                    append("   Snippet: ${s.snippet}\n")
                }
            }
            append("Please synthesize the above fresh web search data directly into your answer!\n")
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
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

package com.bookparser.app.web.search.parsers

import com.bookparser.app.AppLogger
import com.bookparser.app.web.search.BookFormat
import com.bookparser.app.web.search.DohHttpClient
import com.bookparser.app.web.search.SearchOutcome
import com.bookparser.app.web.search.SearchResult
import com.bookparser.app.web.search.SiteParser
import com.bookparser.app.web.search.WebViewSearcher
import org.jsoup.Jsoup

class LibrainParser : SiteParser {

    companion object {
        private const val TAG = "LIBRAIN"
    }

    override suspend fun search(query: String, domain: String, doh: DohHttpClient): SearchOutcome {
        val cleanDomain = normalizeDomain(domain)
        val url = "https://$cleanDomain/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        AppLogger.i(TAG, "WebView search URL: $url")

        var html = WebViewSearcher.get().fetchHtml(url, waitForStableMs = 2500, timeoutMs = 12000)
            ?: return SearchOutcome.Failure("Librain недоступен")
        if (isCookieChallenge(html)) {
            AppLogger.i(TAG, "Librain returned a cookie challenge; retrying with saved cookies")
            html = WebViewSearcher.get().fetchHtml(url, waitForStableMs = 1500, timeoutMs = 8000)
                ?: return SearchOutcome.Failure("Librain не прошёл проверку браузера")
        }
        if (isCookieChallenge(html)) {
            return SearchOutcome.Failure("Librain не прошёл проверку браузера")
        }

        val results = parseHtml(html, cleanDomain)
        AppLogger.i(TAG, "Parsed ${results.size} results")
        return if (results.isNotEmpty()) SearchOutcome.Success(results) else SearchOutcome.Empty
    }

    private fun normalizeDomain(domain: String): String = domain
        .trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    private fun isCookieChallenge(html: String): Boolean {
        val text = Jsoup.parse(html).text()
        return html.contains("set_cookie", ignoreCase = true) ||
            text.contains("Checking your browser", ignoreCase = true)
    }

    internal fun parseHtml(html: String, usedDomain: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()

        val cards = doc.select(
            ".search-results-list .card, .search-results-list article, " +
                "a[href^=\"/\"][href*=/][href!=\"/\"]"
        )
        AppLogger.i(TAG, "Found ${cards.size} candidate elements")

        for (element in cards) {
            val titleLink = when {
                element.tagName() == "a" -> element
                else -> element.selectFirst("p.fw-bold a, a.fw-bold, a[href]")
            } ?: continue

            val rawHref = titleLink.attr("href").trim()
            if (rawHref.isEmpty() || rawHref.startsWith("#") || rawHref.contains("/search")) continue
            val bookUrl = when {
                rawHref.startsWith("http://") || rawHref.startsWith("https://") -> rawHref
                rawHref.startsWith("/") -> "https://$usedDomain$rawHref"
                else -> "https://$usedDomain/$rawHref"
            }
            if (!seen.add(bookUrl)) continue

            val title = titleLink.text().trim()
            if (title.length < 2) continue

            val author = element.selectFirst("p.text-muted.small, .text-muted, [class*=author]")
                ?.text()
                ?.replace("bi-person", "")
                ?.trim()
                .orEmpty()

            results.add(
                SearchResult(
                    title = title,
                    author = author,
                    pageUrl = bookUrl,
                    formats = listOf(BookFormat("Открыть", bookUrl))
                )
            )
            if (results.size >= 30) break
        }
        return results
    }
}

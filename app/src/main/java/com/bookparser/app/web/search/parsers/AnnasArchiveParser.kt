package com.bookparser.app.web.search.parsers

import com.bookparser.app.AppLogger
import com.bookparser.app.web.search.BookFormat
import com.bookparser.app.web.search.DohHttpClient
import com.bookparser.app.web.search.SearchOutcome
import com.bookparser.app.web.search.SearchResult
import com.bookparser.app.web.search.SiteParser
import com.bookparser.app.web.search.WebViewSearcher
import org.jsoup.Jsoup

class AnnasArchiveParser : SiteParser {

    companion object {
        private const val TAG = "ANNAS"
        private val FALLBACK_DOMAINS = listOf(
            "annas-archive.gd",
            "annas-archive.gl",
            "annas-archive.gs",
            "annas-archive.cc",
            "annas-archive.se",
            "annas-archive.org"
        )

        internal fun isChallengePage(html: String): Boolean {
            val text = Jsoup.parse(html).text()
            return text.contains("Antibot solution", ignoreCase = true) ||
                text.contains("Checking your browser", ignoreCase = true) ||
                text.contains("Please wait a few seconds", ignoreCase = true) ||
                text.contains("DDoS-Guard", ignoreCase = true) ||
                text.contains("cf-chl", ignoreCase = true)
        }
    }

    override suspend fun search(query: String, domain: String, doh: DohHttpClient): SearchOutcome {
        val cleanDomain = normalizeDomain(domain)
        val domainsToTry = (listOf(cleanDomain) + FALLBACK_DOMAINS).distinct()
        val webView = WebViewSearcher.get()

        for (currentDomain in domainsToTry) {
            val url = "https://$currentDomain/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            AppLogger.i(TAG, "Trying with WebView: $url")

            var fetched = webView.fetchHtml(url, waitForStableMs = 4000, timeoutMs = 12000)
            if (fetched.isNullOrBlank()) {
                AppLogger.i(TAG, "Domain $currentDomain returned no HTML")
                continue
            }
            if (isChallengePage(fetched)) {
                AppLogger.i(TAG, "Domain $currentDomain returned an antibot page; retrying with saved cookies")
                fetched = webView.fetchHtml(url, waitForStableMs = 2000, timeoutMs = 8000)
            }
            if (fetched.isNullOrBlank() || isChallengePage(fetched)) {
                AppLogger.i(TAG, "Domain $currentDomain did not pass antibot verification")
                continue
            }

            val results = parseHtml(fetched, currentDomain)
            if (results.isNotEmpty()) {
                AppLogger.i(TAG, "Success from $currentDomain: ${results.size} results")
                return SearchOutcome.Success(results)
            }

            val text = Jsoup.parse(fetched).text()
            val isSearchPage = text.contains("Anna", ignoreCase = true) &&
                (text.contains("Search", ignoreCase = true) ||
                    text.contains("Results", ignoreCase = true) ||
                    text.contains("Результаты", ignoreCase = true))
            if (isSearchPage) {
                AppLogger.i(TAG, "Domain $currentDomain returned a valid empty search page")
                return SearchOutcome.Empty
            }
        }

        return SearchOutcome.Failure("Anna's Archive недоступен (все зеркала не ответили)")
    }

    private fun normalizeDomain(domain: String): String = domain
        .trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    internal fun parseHtml(html: String, usedDomain: String): List<SearchResult> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        val md5Regex = Regex("/md5/([a-f0-9]{32})", RegexOption.IGNORE_CASE)

        for (a in doc.select("a[href*=\"/md5/\"]")) {
            val md5 = md5Regex.find(a.attr("href"))?.groupValues?.get(1)?.lowercase() ?: continue
            val bookUrl = "https://$usedDomain/md5/$md5"
            val title = a.text().trim().split("\n")[0].trim()
            if (title.length < 2) continue
            if (!seen.add(bookUrl)) continue

            val parent = a.parent()
            val siblingLinks = parent?.select("a[href*=\"/search?q=\"]") ?: emptyList()
            val author = siblingLinks.firstOrNull()?.text()?.trim().orEmpty()
            val fullText = parent?.text().orEmpty()
            val format = Regex("\\b(pdf|epub|fb2|mobi|djvu|azw3|txt)\\b", RegexOption.IGNORE_CASE)
                .find(fullText)?.groupValues?.get(1)?.uppercase()

            results.add(
                SearchResult(
                    title = title,
                    author = author,
                    pageUrl = bookUrl,
                    formats = listOf(BookFormat(format ?: "Открыть", bookUrl))
                )
            )
            if (results.size >= 30) break
        }
        return results
    }
}

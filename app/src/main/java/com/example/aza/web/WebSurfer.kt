package com.example.aza.web

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.regex.Pattern

object WebSurfer {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .certificatePinner(com.example.aza.security.SslPinningUtility.certificatePinner)
        .build()

    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Surfs any given website URL and extracts clean, readable main body text content.
     */
    fun surfUrl(urlStr: String): String {
        var url = urlStr.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return "Error: Received HTTP response code ${response.code} from website."
                }
                val html = response.body?.string() ?: ""
                if (html.isEmpty()) {
                    return "Error: Website returned an empty page."
                }
                return extractCleanText(html)
            }
        } catch (e: Exception) {
            return "Failed to connect or download page: ${e.localizedMessage ?: "Unknown Error"}"
        }
    }

    /**
     * Performs a web search query on DuckDuckGo and extracts the titles, snippets, and links.
     */
    fun searchWeb(query: String): List<SearchResult> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
        
        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        val results = mutableListOf<SearchResult>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val html = response.body?.string() ?: ""
                
                // Parse duckduckgo html results simply and safely
                val resultPattern = Pattern.compile(
                    "<a class=\"result__url\" href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?" +
                    "<a class=\"result__snippet\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL
                )
                val matcher = resultPattern.matcher(html)
                while (matcher.find() && results.size < 8) {
                    val rawUrl = matcher.group(1) ?: ""
                    val cleanedUrl = cleanDdgUrl(rawUrl)
                    val title = stripHtml(matcher.group(2) ?: "").trim()
                    val snippet = stripHtml(matcher.group(3) ?: "").trim()
                    if (cleanedUrl.isNotEmpty() && title.isNotEmpty()) {
                        results.add(SearchResult(title = title, url = cleanedUrl, snippet = snippet))
                    }
                }
                
                // Fallback simpler parsing if the above exact regex fails due to DDG design variations
                if (results.isEmpty()) {
                    val linkPattern = Pattern.compile("<a class=\"result__snippet\" href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL)
                    val linkMatcher = linkPattern.matcher(html)
                    while (linkMatcher.find() && results.size < 5) {
                        val rawUrl = linkMatcher.group(1) ?: ""
                        val cleanedUrl = cleanDdgUrl(rawUrl)
                        val text = stripHtml(linkMatcher.group(2) ?: "").trim()
                        if (cleanedUrl.isNotEmpty()) {
                            results.add(SearchResult(title = "Web Result", url = cleanedUrl, snippet = text))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore search errors
        }
        return results
    }

    private fun cleanDdgUrl(rawUrl: String): String {
        var u = rawUrl
        if (u.contains("uddg=")) {
            val parts = u.split("uddg=")
            if (parts.size > 1) {
                val encodedUrl = parts[1].split("&").firstOrNull() ?: ""
                u = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
            }
        }
        if (u.startsWith("//")) {
            u = "https:$u"
        }
        return u
    }

    /**
     * Clean and strip HTML tag blocks, styles, scripts, and format the page content.
     */
    fun extractCleanText(html: String): String {
        var clean = html
        
        // Remove style blocks
        clean = clean.replace("(?s)<style\\b[^>]*>.*?</style>".toRegex(), "")
        // Remove script blocks
        clean = clean.replace("(?s)<script\\b[^>]*>.*?</script>".toRegex(), "")
        // Remove comments
        clean = clean.replace("(?s)<!--.*?-->".toRegex(), "")
        
        // Format common block components for better readability
        clean = clean.replace("(?i)<h[1-6]\\b[^>]*>".toRegex(), "\n\n### ")
        clean = clean.replace("(?i)</h[1-6]>".toRegex(), "\n\n")
        clean = clean.replace("(?i)<p\\b[^>]*>".toRegex(), "\n\n")
        clean = clean.replace("(?i)<br\\s*/?>".toRegex(), "\n")
        clean = clean.replace("(?i)<li\\b[^>]*>".toRegex(), "\n• ")
        clean = clean.replace("(?i)<tr\\b[^>]*>".toRegex(), "\n")
        clean = clean.replace("(?i)<td\\b[^>]*>".toRegex(), " | ")
        
        // Strip all other HTML tags
        clean = clean.replace("<[^>]+>".toRegex(), "")
        
        // Unescape standard HTML character reference sequences
        clean = clean.replace("&amp;", "&")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&ndash;", "-")
            .replace("&mdash;", "—")

        // Clean up excessive spacing
        val lines = clean.split("\n")
        val formattedLines = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                formattedLines.add(trimmed)
            }
        }
        
        val finalResult = formattedLines.joinToString("\n")
        return if (finalResult.length > 8000) {
            finalResult.substring(0, 8000) + "\n\n... [Content truncated due to length] ..."
        } else {
            finalResult
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace("<[^>]+>".toRegex(), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
    }
}

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

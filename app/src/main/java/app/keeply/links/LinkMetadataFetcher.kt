package app.keeply.links

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polite, single-request title lookup for a saved link.
 * - one GET, 5 s timeouts, at most 64 KB read, HTML only
 * - honest User-Agent, no cookies, no JavaScript, no follow-up requests for images
 * - failures are silent: the user simply types a title
 */
class LinkMetadataFetcher {

    suspend fun fetchTitle(url: String): String? = fetchHtml(url)?.let(::extractTitle)

    /**
     * The page's preview image (og:image / twitter:image), as an absolute https URL. It's shown
     * straight from the website, so nothing is uploaded or stored.
     */
    suspend fun fetchImage(url: String): String? =
        youTubeThumbnail(url) ?: fetchHtml(url, IMAGE_MAX_BYTES)?.let { extractImage(it, url) }

    private suspend fun fetchHtml(url: String, maxBytes: Int = MAX_BYTES): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Keeply/1.0 (link title preview)")
                setRequestProperty("Accept", "text/html")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                if (conn.contentType?.contains("html", ignoreCase = true) != true) return@runCatching null
                val bytes = conn.inputStream.use { input ->
                    val buf = ByteArray(maxBytes)
                    var read = 0
                    while (read < maxBytes) {
                        val n = input.read(buf, read, maxBytes - read)
                        if (n <= 0) break
                        read += n
                    }
                    buf.copyOf(read)
                }
                String(bytes, Charsets.UTF_8)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        private const val MAX_BYTES = 64 * 1024
        /** Big sites put og:image after a lot of inline script; look further for it. */
        private const val IMAGE_MAX_BYTES = 512 * 1024

        private val YT_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

        /**
         * YouTube pages are heavy and often show a consent page to non-browsers, but every video
         * has a public thumbnail at a fixed address, so no page fetch is needed.
         */
        fun youTubeThumbnail(url: String): String? {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
            val host = uri.host?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("music.") ?: return null
            val path = uri.path.orEmpty()
            val query = uri.rawQuery.orEmpty().split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            val id = when (host) {
                "youtu.be" -> path.trim('/').substringBefore('/')
                "youtube.com", "youtube-nocookie.com" -> when {
                    path == "/watch" -> query["v"]
                    path.startsWith("/shorts/") || path.startsWith("/embed/") || path.startsWith("/live/") -> path.split('/').getOrNull(2)
                    else -> null
                }
                else -> null
            } ?: return null
            return if (YT_ID.matches(id)) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else null
        }
        private val OG_TITLE = Regex("""<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val TITLE = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        private val OG_IMAGE = Regex("""<meta[^>]+(?:property|name)=["'](?:og:image(?::secure_url)?|twitter:image)["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val OG_IMAGE_REV = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["'](?:og:image(?::secure_url)?|twitter:image)["']""", RegexOption.IGNORE_CASE)

        fun extractImage(html: String, pageUrl: String): String? {
            val raw = (OG_IMAGE.find(html) ?: OG_IMAGE_REV.find(html))?.groupValues?.get(1)?.replace("&amp;", "&")?.trim() ?: return null
            val abs = runCatching { java.net.URL(java.net.URL(pageUrl), raw).toString() }.getOrNull() ?: return null
            // Only https images: no mixed content, no local files.
            return abs.takeIf { it.startsWith("https://") }
        }

        fun extractTitle(html: String): String? {
            val raw = OG_TITLE.find(html)?.groupValues?.get(1) ?: TITLE.find(html)?.groupValues?.get(1) ?: return null
            return raw.replace(Regex("\\s+"), " ")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">")
                .trim().take(200).ifEmpty { null }
        }
    }
}

package app.keeply.core.domain

import java.net.URI

/** Extracts and validates links from user input or Share Sheet text. */
object UrlParser {
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    data class ParsedUrl(val url: String, val domain: String)

    /** Shared text often looks like "Look at this! https://…"; take the first URL. */
    fun extractFirst(text: String): String? =
        URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')

    /** Accepts "example.com/x" by assuming https. Returns null for anything that isn't http(s). */
    fun parse(input: String): ParsedUrl? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return null
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        return ParsedUrl(url = withScheme, domain = host.removePrefix("www."))
    }
}

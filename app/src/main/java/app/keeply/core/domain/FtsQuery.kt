package app.keeply.core.domain

/**
 * Turns free text into a safe SQLite FTS4 MATCH expression with prefix matching:
 * "apple mac" -> "apple* mac*". All FTS operators and quotes are stripped so user
 * input can never produce a syntax error or change query semantics.
 */
object FtsQuery {
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")
    private val RESERVED = setOf("and", "or", "not", "near")

    fun build(raw: String): String? {
        val tokens = raw.lowercase()
            .split(TOKEN_SPLIT)
            .filter { it.isNotBlank() && it !in RESERVED }
            .take(8)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}

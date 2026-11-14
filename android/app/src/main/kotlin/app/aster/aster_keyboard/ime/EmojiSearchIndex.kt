package app.aster.aster_keyboard.ime

/** Immutable, bundled CLDR metadata. Construct and search on the IME worker. */
class EmojiSearchIndex(lines: Sequence<String>) {
    private data class Entry(val emoji: String, val names: List<String>, val phrases: List<String>, val tokens: List<String>) {
        val nameTokens = names.map { it.split(' ') }
    }
    private data class Match(val emoji: String, val score: Int, val nameLength: Int)
    private val entries = lines.mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4) return@mapNotNull null
        val names = parts.slice(1..2).map(::normalize)
        // Normalize one keyword block per emoji, rather than recompiling a
        // pattern and running Unicode normalization for every individual alias.
        val aliases = phraseSeparators.replace(plain(parts[3]), " ").split('|').map { it.trim() }
        val phrases = (names + aliases).distinct()
        Entry(parts[0], names, phrases, phrases.flatMap { it.split(' ') }.filter { it.isNotEmpty() }.distinct())
    }.toList()

    fun search(query: String): List<String> {
        if (query.isBlank() || query.length > EmojiSearchSession.MAX_LENGTH) return emptyList()
        val text = normalize(query)
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        return entries.asSequence().takeWhile { !Thread.currentThread().isInterrupted }.mapNotNull { entry ->
            val nameLength = if (tokens.isEmpty()) Int.MAX_VALUE else entry.names.indices.filter { index ->
                tokens.all { token -> entry.nameTokens[index].any { it.startsWith(token) } }
            }.minOfOrNull { entry.names[it].length } ?: Int.MAX_VALUE
            val score = when {
                entry.emoji.replace("\uFE0F", "") == query.trim().replace("\uFE0F", "") -> 0
                tokens.isEmpty() -> return@mapNotNull null
                text in entry.names -> 1
                nameLength != Int.MAX_VALUE -> 2
                text in entry.phrases -> 3
                tokens.all { token -> entry.tokens.any { it.startsWith(token) } } -> 4
                else -> return@mapNotNull null
            }
            Match(entry.emoji, score, nameLength)
        }.sortedWith(compareBy<Match> { it.score }.thenBy { it.nameLength }).map { it.emoji }.distinct().toList()
    }

    companion object {
        private val separators = Regex("[^\\p{L}\\p{N}]+")
        private val phraseSeparators = Regex("[^\\p{L}\\p{N}|]+")
        private fun plain(text: String) = WordLexicon.fold(WordLexicon.normalize(text))
        private fun normalize(text: String) = separators.replace(plain(text), " ").trim()
    }
}

/** Search text is transient UI state, never editor text or a saved preference. */
class EmojiSearchSession {
    var query: String = ""
        private set
    fun append(text: String) {
        query = (query + text.filter { !it.isISOControl() }).take(MAX_LENGTH)
        if (query.lastOrNull()?.isHighSurrogate() == true) query = query.dropLast(1)
    }
    fun backspace() {
        if (query.isNotEmpty()) query = query.substring(0, query.offsetByCodePoints(query.length, -1))
    }
    fun clear() { query = "" }
    companion object { const val MAX_LENGTH = 64 }
}

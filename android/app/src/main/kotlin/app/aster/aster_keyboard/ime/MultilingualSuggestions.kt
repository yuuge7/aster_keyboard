package app.aster.aster_keyboard.ime

/** Romanian typing can mix English words without changing the keyboard layout. */
class MultilingualSuggestions(private val dictionary: (String) -> WordLexicon?) {
    private data class Match(val candidate: WordLexicon.Candidate, val distance: Int, val languageOrder: Int)

    /** Constant-time common fixes are safe on the key-event path, even at cold start. */
    fun immediateCorrection(input: String, language: String, restoreDiacritics: Boolean = true): String? {
        val primary = dictionary(language)
        val common = if (primary == null) WordLexicon.commonCorrection(language, input, restoreDiacritics)
            else primary.immediateCorrection(input, restoreDiacritics)
        if (language != "ro" || common != null || primary?.contains(input) == true) return common
        val english = dictionary("en")
        return if (english == null) WordLexicon.commonCorrection("en", input)
            else english.immediateCorrection(input)
    }

    /** Worker-only: known spellings survive; equally close cross-language fixes stay suggestions. */
    fun correction(input: String, language: String, restoreDiacritics: Boolean = true): String? {
        if (protectsEnglish(input, language, restoreDiacritics)) return null
        immediateCorrection(input, language, restoreDiacritics)?.let { return it }
        val primary = dictionary(language)
        val first = primary?.correction(input, restoreDiacritics)
        if (language != "ro" || primary?.contains(input) == true) return first
        val second = dictionary("en")?.correction(input) ?: return first
        if (first == null || WordLexicon.normalize(first) == WordLexicon.normalize(second)) return second
        val plain = WordLexicon.fold(WordLexicon.normalize(input))
        val primaryDistance = distance(plain, WordLexicon.fold(WordLexicon.normalize(first)))
        val englishDistance = distance(plain, WordLexicon.fold(WordLexicon.normalize(second)))
        return when {
            primaryDistance < englishDistance -> first
            englishDistance < primaryDistance -> second
            else -> null
        }
    }

    fun suggestions(input: String, language: String, personal: Map<String, Set<String>> = emptyMap()): List<String> {
        // Preserve the existing English suggestion and autocorrect policy.
        if (language != "ro") return dictionary(language)?.suggestions(input, personal[language].orEmpty()).orEmpty()
        if (input.isBlank() || input.length > 32) return emptyList()
        val normalized = WordLexicon.normalize(input)
        val plain = WordLexicon.fold(normalized)
        return languages(language).flatMapIndexed { index, code ->
            dictionary(code)?.suggestionCandidates(input, personal[code].orEmpty()).orEmpty().map {
                val candidate = WordLexicon.fold(WordLexicon.normalize(it.word))
                // Completing an exact prefix is one suggestion operation, rather
                // than a typo for every remaining letter of a useful long word.
                val closeness = if (candidate != plain && candidate.startsWith(plain)) 1 else distance(plain, candidate)
                Match(it, closeness, index)
            }
        }.sortedWith(compareBy<Match> { it.distance }
            .thenByDescending { it.candidate.personal }
            .thenByDescending { it.candidate.frequency }
            .thenBy { it.languageOrder }
            .thenBy { WordLexicon.normalize(it.candidate.word) })
            .distinctBy { WordLexicon.normalize(it.candidate.word) }
            .filter { WordLexicon.normalize(it.candidate.word) != normalized }
            .take(3).map { WordLexicon.matchCase(input, it.candidate.word) }
    }

    /** Known English words must survive Romanian autocorrect, including rarer terms. */
    fun protectsEnglish(input: String, language: String, restoreDiacritics: Boolean = true): Boolean =
        language == "ro" && dictionary("en")?.contains(input) == true &&
            // Frequency lists include some misspellings. A curated English fix
            // may replace those only when the input is not a Romanian word.
            !(dictionary("ro")?.contains(input) != true && dictionary("en")?.immediateCorrection(input) != null) &&
            !(restoreDiacritics && (dictionary("ro")?.hasDiacriticAlternative(input) == true ||
                WordLexicon.commonDiacriticAlternative(input)))

    companion object {
        fun languages(active: String): List<String> = if (active == "ro") listOf("ro", "en") else listOf(active)

        /** Edit distance with adjacent transpositions counted as one typo. */
        internal fun distance(first: String, second: String, limit: Int = maxOf(first.length, second.length)): Int {
            if (kotlin.math.abs(first.length - second.length) > limit) return limit + 1
            val costs = Array(first.length + 1) { IntArray(second.length + 1) }
            for (i in 0..first.length) costs[i][0] = i
            for (j in 0..second.length) costs[0][j] = j
            for (i in 1..first.length) {
                if (Thread.currentThread().isInterrupted) return limit + 1
                var minimum = costs[i][0]
                for (j in 1..second.length) {
                    costs[i][j] = minOf(costs[i - 1][j] + 1, costs[i][j - 1] + 1,
                        costs[i - 1][j - 1] + if (first[i - 1] == second[j - 1]) 0 else 1)
                    if (i > 1 && j > 1 && first[i - 1] == second[j - 2] && first[i - 2] == second[j - 1]) {
                        costs[i][j] = minOf(costs[i][j], costs[i - 2][j - 2] + 1)
                    }
                    minimum = minOf(minimum, costs[i][j])
                }
                if (minimum > limit) return limit + 1
            }
            return costs[first.length][second.length]
        }
    }
}

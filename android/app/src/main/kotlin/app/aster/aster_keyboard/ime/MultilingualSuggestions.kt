package app.aster.aster_keyboard.ime

/** Romanian typing can mix English words without changing the keyboard layout. */
class MultilingualSuggestions(private val dictionary: (String) -> WordLexicon?) {
    private data class Match(val candidate: WordLexicon.Candidate, val distance: Int, val languageOrder: Int)

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
            !(restoreDiacritics && dictionary("ro")?.hasDiacriticAlternative(input) == true)

    companion object {
        fun languages(active: String): List<String> = if (active == "ro") listOf("ro", "en") else listOf(active)

        /** Edit distance with adjacent transpositions counted as one typo. */
        internal fun distance(first: String, second: String): Int {
            val costs = Array(first.length + 1) { IntArray(second.length + 1) }
            for (i in 0..first.length) costs[i][0] = i
            for (j in 0..second.length) costs[0][j] = j
            for (i in 1..first.length) for (j in 1..second.length) {
                costs[i][j] = minOf(costs[i - 1][j] + 1, costs[i][j - 1] + 1,
                    costs[i - 1][j - 1] + if (first[i - 1] == second[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && first[i - 1] == second[j - 2] && first[i - 2] == second[j - 1]) {
                    costs[i][j] = minOf(costs[i][j], costs[i - 2][j - 2] + 1)
                }
            }
            return costs[first.length][second.length]
        }
    }
}

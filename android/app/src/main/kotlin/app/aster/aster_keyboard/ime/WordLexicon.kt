package app.aster.aster_keyboard.ime

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

/** Offline frequency dictionary; never learns, saves, or sends typed text. */
class WordLexicon(entries: List<Pair<String, Int>>, private val language: String) {
    private val words = HashMap<String, Int>().apply {
        entries.forEach { (word, count) ->
            val normalized = normalize(word)
            this[normalized] = maxOf(this[normalized] ?: 0, count)
        }
    }
    private val folded = HashMap<String, String>().apply {
        words.forEach { (word, count) ->
            val key = fold(word); val previous = this[key]
            if (previous == null || count > words.getValue(previous)) this[key] = word
        }
    }
    private val prefixes = words.keys.groupBy { it.take(2) }
    private val foldedPrefixes = folded.keys.groupBy { it.take(2) }
    private val alphabet = if (language == "ro") "abcdefghijklmnopqrstuvwxyzăâîșț'" else "abcdefghijklmnopqrstuvwxyz'"
    fun contains(word: String) = normalize(word) in words
    fun isCommon(word: String) = (words[normalize(word)] ?: 0) >= 10000
    internal fun hasDiacriticAlternative(input: String): Boolean {
        val word = normalize(input)
        return folded[fold(word)]?.let { it != word } == true
    }
    fun suggestions(input: String, personal: Set<String> = emptySet()): List<String> {
        if (input.isBlank() || input.length > 32) return emptyList()
        val word = normalize(input)
        val candidates = neighboringWords(word)
        val corrections = candidates.filter { it in words && it != word }.sortedByDescending { score(it, word) }
        val completions = if (word.length >= 2) prefixes[word.take(2)].orEmpty().asSequence()
            .filter { it.startsWith(word) && it != word }.sortedByDescending { words[it] }.take(3).toList() else emptyList()
        val custom = personal.filter { normalize(it).startsWith(word) && normalize(it) != word }.take(3)
        return (custom + corrections.take(3) + completions).distinct().take(3).map { matchCase(input, it) }
    }
    internal data class Candidate(val word: String, val frequency: Int, val personal: Boolean = false)
    /** Keep candidates unranked across languages until the shared ranking step. */
    internal fun suggestionCandidates(input: String, personal: Set<String>): List<Candidate> {
        if (input.isBlank() || input.length > 32) return emptyList()
        val word = normalize(input); val plain = fold(word)
        val nearby = neighboringWords(word).filter { it in words } +
            neighboringWords(plain).mapNotNull { folded[it] }
        val completions = if (word.length >= 2) foldedPrefixes[plain.take(2)].orEmpty().asSequence()
            .filter { it.startsWith(plain) }.map { folded.getValue(it) }
            .sortedWith(compareByDescending<String> { words[it] }.thenBy { it.length })
            .take(6).toList() else emptyList()
        val custom = personal.filter {
            val normalized = normalize(it); val folded = fold(normalized)
            normalized != word && (folded.startsWith(plain) || MultilingualSuggestions.distance(plain, folded) <= 1)
        }.map { Candidate(it, 0, personal = true) }
        return custom + (nearby + completions).distinct().filter { it != word }.map { Candidate(it, words.getValue(it)) }
    }
    private fun neighboringWords(word: String): Set<String> {
        val candidates = mutableSetOf<String>()
        folded[fold(word)]?.let { candidates.add(it) }
        for (index in word.indices) {
            if (Thread.currentThread().isInterrupted) return emptySet()
            candidates.add(word.removeRange(index, index + 1))
            if (index + 1 < word.length) candidates.add(word.substring(0, index) + word[index + 1] + word[index] + word.substring(index + 2))
            alphabet.forEach { candidates.add(word.substring(0, index) + it + word.substring(index + 1)) }
        }
        for (index in 0..word.length) {
            if (Thread.currentThread().isInterrupted) return emptySet()
            alphabet.forEach { candidates.add(word.substring(0, index) + it + word.substring(index)) }
        }
        return candidates
    }
    /** A bounded map lookup for the delimiter path; edit searches stay on the worker. */
    fun immediateCorrection(input: String, restoreDiacritics: Boolean = true): String? {
        if (input.length !in 2..32 || input.any { !it.isLetter() && it !in "'’" }) return null
        if (input.drop(1).any { it.isUpperCase() } && input != input.uppercase(Locale.ROOT)) return null
        val common = if (language == "en") englishCorrections else if (restoreDiacritics) romanianCorrections else emptyMap()
        return common[normalize(input)]?.takeIf { it in words }?.let { matchCase(input, it) }
    }
    fun correction(input: String, restoreDiacritics: Boolean = true, protected: Set<String> = emptySet()): String? {
        val word = normalize(input)
        if (word.length !in 2..32 || input.any { !it.isLetter() && it !in "'’" } || protected.any { normalize(it) == word }) return null
        if (input.drop(1).any { it.isUpperCase() } && input != input.uppercase(Locale.ROOT)) return null
        immediateCorrection(input, restoreDiacritics)?.let { return it }
        if (word in words || word.length < 3) return null
        val choices = suggestions(word).map(::normalize).filter {
            !it.startsWith(word) && (restoreDiacritics || fold(it) != fold(word))
        }
        val best = choices.firstOrNull() ?: return null
        if ((words[best] ?: 0) < 500) return null
        val second = choices.getOrNull(1)
        if (second != null && score(best, word) < score(second, word) + .8) return null
        return matchCase(input, best)
    }
    private fun score(candidate: String, input: String) = ln((words[candidate] ?: 1).toDouble()) +
        (if (fold(candidate) == fold(input)) 3.0 else 0.0)
    companion object {
        private val englishCorrections = mapOf("teh" to "the", "adn" to "and", "recieve" to "receive", "dont" to "don't", "doesnt" to "doesn't", "cant" to "can't", "wont" to "won't")
        private val romanianCorrections = mapOf("multumesc" to "mulțumesc", "buna" to "bună", "maine" to "mâine", "romana" to "română", "intradevar" to "într-adevăr")
        fun normalize(word: String): String = Normalizer.normalize(word.replace('ş', 'ș').replace('ţ', 'ț').replace('Ş', 'Ș').replace('Ţ', 'Ț').replace('’', '\''), Normalizer.Form.NFC).lowercase(Locale.ROOT)
        fun fold(word: String): String = buildString {
            Normalizer.normalize(word, Normalizer.Form.NFD).forEach { char ->
                val type = Character.getType(char)
                if (type != Character.NON_SPACING_MARK.toInt() && type != Character.COMBINING_SPACING_MARK.toInt() &&
                    type != Character.ENCLOSING_MARK.toInt()) append(char)
            }
        }
        fun matchCase(input: String, word: String): String = when {
            input.length > 1 && input == input.uppercase(Locale.ROOT) -> word.uppercase(Locale.ROOT)
            input.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}

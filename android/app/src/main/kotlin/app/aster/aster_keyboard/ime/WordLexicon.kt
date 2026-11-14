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
    // Only frequent words participate in the broader search. The full vocabulary
    // still protects known spellings and supplies exact/prefix/one-edit matches.
    private val frequent = words.keys.filter { words.getValue(it) >= 500 && it.length >= 5 }
    private val fuzzyPrefixes = frequent.groupBy { fold(it).take(2) }
    private val fuzzySuffixes = frequent.groupBy { fold(it).takeLast(2) }
    private val fuzzyMiddles = frequent.groupBy { middle(fold(it)) }
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
        val candidates = nearbyWords(word)
        val completions = if (word.length >= 2) prefixes[word.take(2)].orEmpty().asSequence()
            .filter { it.startsWith(word) && it != word }.sortedByDescending { words[it] }.take(3).toList() else emptyList()
        val custom = personal.filter {
            val normalized = normalize(it)
            normalized != word && (normalized.startsWith(word) || MultilingualSuggestions.distance(word, normalized, 1) <= 1)
        }.sortedBy { normalize(it) }.take(3)
        val ranked = (candidates + completions).distinct().filter { it != word }
            .map { it to score(it, word, completion = it.startsWith(word)) }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }).map { it.first }
        val common = immediateCorrection(word)
        return (custom + listOfNotNull(common) + ranked).distinct().take(3).map { matchCase(input, it) }
    }
    internal data class Candidate(val word: String, val frequency: Int, val personal: Boolean = false)
    /** Keep candidates unranked across languages until the shared ranking step. */
    internal fun suggestionCandidates(input: String, personal: Set<String>): List<Candidate> {
        if (input.isBlank() || input.length > 32) return emptyList()
        val word = normalize(input); val plain = fold(word)
        val nearby = nearbyWords(word)
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
    private fun nearbyWords(word: String): Set<String> {
        val plain = fold(word)
        val neighbors = neighboringWords(word)
        val nearby = (neighbors.filter { it in words } +
            (if (plain == word) neighbors else neighboringWords(plain)).mapNotNull { folded[it] }).toMutableSet()
        if (word.length >= 5) {
            // Matching an end or the middle tolerates errors elsewhere without
            // scanning 100k entries or generating an unbounded edits-of-edits set.
            val broader = (fuzzyPrefixes[plain.take(2)].orEmpty() + fuzzySuffixes[plain.takeLast(2)].orEmpty() +
                fuzzyMiddles[middle(plain)].orEmpty()).distinct()
            for (candidate in broader) {
                if (Thread.currentThread().isInterrupted) return emptySet()
                if (MultilingualSuggestions.distance(plain, fold(candidate), 2) <= 2) nearby.add(candidate)
            }
        }
        return nearby
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
    fun immediateCorrection(input: String, restoreDiacritics: Boolean = true): String? =
        commonCorrection(language, input, restoreDiacritics)?.takeIf { normalize(it) in words }
    fun correction(input: String, restoreDiacritics: Boolean = true, protected: Set<String> = emptySet()): String? {
        val word = normalize(input)
        if (word.length !in 2..32 || input.any { !it.isLetter() && it !in "'’" } || protected.any { normalize(it) == word }) return null
        if (input.drop(1).any { it.isUpperCase() } && input != input.uppercase(Locale.ROOT)) return null
        immediateCorrection(input, restoreDiacritics)?.let { return it }
        if (word in words || word.length < 3) return null
        // Completion ranking must not hide a spelling correction or turn a
        // partial word into an automatic completion at a delimiter.
        val choices = nearbyWords(word).filter {
            it != word && !it.startsWith(word) && (restoreDiacritics || fold(it) != fold(word))
        }.map { it to score(it, word) }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }).map { it.first }
        val best = choices.firstOrNull() ?: return null
        val edits = MultilingualSuggestions.distance(fold(word), fold(best), 2)
        if ((words[best] ?: 0) < if (edits >= 2) 5000 else 500) return null
        val second = choices.getOrNull(1)
        if (second != null && score(best, word) < score(second, word) + if (edits >= 2) 1.6 else .8) return null
        return matchCase(input, best)
    }
    private fun score(candidate: String, input: String, completion: Boolean = false): Double {
        val plain = fold(input); val target = fold(candidate)
        val edits = if (completion) 1 else MultilingualSuggestions.distance(plain, target, 2)
        return ln((words[candidate] ?: 1).toDouble()) - edits * 2.5 + if (target == plain) 3.0 else 0.0
    }
    companion object {
        /** Curated fixes also work while the larger frequency lists are loading. */
        internal fun commonCorrection(language: String, input: String, restoreDiacritics: Boolean = true): String? {
            if (input.length !in 2..32 || input.any { !it.isLetter() && it !in "'’" }) return null
            if (input.drop(1).any { it.isUpperCase() } && input != input.uppercase(Locale.ROOT)) return null
            val common = when (language) {
                "en" -> englishCorrections
                "ro" -> if (restoreDiacritics) romanianCorrections else emptyMap()
                else -> emptyMap()
            }
            return common[normalize(input)]?.let { matchCase(input, it) }
        }
        internal fun commonDiacriticAlternative(input: String): Boolean {
            val restored = commonCorrection("ro", input) ?: return false
            return fold(normalize(restored)) == fold(normalize(input))
        }
        private fun middle(word: String): String {
            val start = (word.length - 2) / 2
            return word.substring(start, start + 2)
        }
        private val englishCorrections = mapOf(
            "teh" to "the", "adn" to "and", "recieve" to "receive", "dont" to "don't", "doesnt" to "doesn't", "cant" to "can't", "wont" to "won't",
            "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't", "didnt" to "didn't",
            "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't", "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't",
            "youre" to "you're", "theyre" to "they're", "weve" to "we've", "youve" to "you've", "theyve" to "they've", "thats" to "that's",
            "whats" to "what's", "heres" to "here's", "theres" to "there's", "whos" to "who's",
            "definately" to "definitely", "definatly" to "definitely", "definetly" to "definitely", "defintely" to "definitely",
            "seperate" to "separate", "seperately" to "separately", "occured" to "occurred", "occurence" to "occurrence",
            "becuase" to "because", "beacuse" to "because", "becasue" to "because", "beutiful" to "beautiful", "beautifull" to "beautiful",
            "tomorow" to "tomorrow", "tommorow" to "tomorrow", "tommorrow" to "tomorrow", "yesturday" to "yesterday",
            "probaly" to "probably", "probbaly" to "probably", "probablly" to "probably",
            "realy" to "really", "reallly" to "really", "finaly" to "finally", "basicly" to "basically", "actualy" to "actually",
            "usualy" to "usually", "especialy" to "especially", "exaclty" to "exactly", "absolutly" to "absolutely",
            "neccessary" to "necessary", "necesary" to "necessary", "neccessery" to "necessary", "unfortunatly" to "unfortunately",
            "wierd" to "weird", "freind" to "friend", "freinds" to "friends", "peopel" to "people", "mesage" to "message",
            "adress" to "address", "addres" to "address", "buisness" to "business", "bussiness" to "business",
            "acheive" to "achieve", "beleive" to "believe", "belive" to "believe", "remeber" to "remember", "rember" to "remember",
            "begining" to "beginning", "comming" to "coming", "runing" to "running", "writting" to "writing", "tryed" to "tried",
            "thier" to "their", "thign" to "thing", "thigns" to "things", "somthing" to "something", "everythign" to "everything",
            "intresting" to "interesting", "intrested" to "interested", "diferent" to "different", "diffrent" to "different",
            "langauge" to "language", "languge" to "language", "keybaord" to "keyboard", "keybord" to "keyboard",
            "pleae" to "please", "plase" to "please", "thansk" to "thanks", "thnaks" to "thanks", "sory" to "sorry",
        )
        private val romanianCorrections = mapOf(
            "multumesc" to "mulțumesc", "buna" to "bună", "maine" to "mâine", "romana" to "română", "intradevar" to "într-adevăr",
            "multumim" to "mulțumim", "multumeste" to "mulțumește", "multumescs" to "mulțumesc", "multumiri" to "mulțumiri",
            "dimineata" to "dimineață", "dimineatza" to "dimineață", "diseara" to "diseară", "saptamana" to "săptămână",
            "saptamani" to "săptămâni", "saptamanal" to "săptămânal", "sambata" to "sâmbătă", "duminica" to "duminică",
            "sanatate" to "sănătate", "sanatos" to "sănătos", "sanatoasa" to "sănătoasă", "sarbatori" to "sărbători",
            "fericita" to "fericită", "frumoasa" to "frumoasă", "draguta" to "drăguță", "dragut" to "drăguț", "iubita" to "iubită",
            "inteleg" to "înțeleg", "intelegi" to "înțelegi", "inteles" to "înțeles", "intelegem" to "înțelegem",
            "intrebare" to "întrebare", "intrebari" to "întrebări", "raspuns" to "răspuns", "raspunde" to "răspunde",
            "intalnire" to "întâlnire", "intalnim" to "întâlnim", "impreuna" to "împreună", "inainte" to "înainte",
            "inapoi" to "înapoi", "inceput" to "început", "incepe" to "începe", "incerc" to "încerc", "invata" to "învață",
            "acasa" to "acasă", "scoala" to "școală", "vacanta" to "vacanță", "dorinta" to "dorință", "viata" to "viață",
            "putin" to "puțin", "putina" to "puțină", "cativa" to "câțiva", "cateva" to "câteva", "niciodata" to "niciodată",
            "cateodata" to "câteodată", "intotdeauna" to "întotdeauna", "deocamdata" to "deocamdată", "bineinteles" to "bineînțeles",
            "fiindca" to "fiindcă", "anunt" to "anunț", "atentie" to "atenție", "informatii" to "informații",
            "astept" to "aștept", "asteptam" to "așteptăm", "gasit" to "găsit", "gasesti" to "găsești", "reusit" to "reușit",
        )
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

package app.aster.aster_keyboard.ime

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class WordLexiconTest {
    private val english = WordLexicon(listOf("the" to 100000, "and" to 90000, "hello" to 40000, "world" to 30000, "word" to 2000, "receive" to 1000, "don't" to 1000, "cat" to 5000, "cot" to 1), "en")
    private val romanian = WordLexicon(listOf("mulțumesc" to 100000, "bună" to 80000, "mâine" to 70000, "română" to 60000, "și" to 10000), "ro")
    @Test fun frequentTyposAndTranspositionsAreCorrected() {
        assertEquals("the", english.correction("teh")); assertEquals("and", english.correction("adn"))
        assertEquals("world", english.correction("wrold")); assertEquals("receive", english.correction("recieve"))
    }
    @Test fun immediateCorrectionsKeepCommonFormsAndLeaveOtherSearchesToTheWorker() {
        assertEquals("The", english.immediateCorrection("Teh"))
        assertEquals("mulțumesc", romanian.immediateCorrection("multumesc"))
        assertNull(romanian.immediateCorrection("multumesc", restoreDiacritics = false))
        assertNull(english.immediateCorrection("tEh"))
        assertNull(english.immediateCorrection("wrold"))
        assertEquals("world", english.correction("wrold"))
    }
    @Test fun curatedCorrectionsWorkBeforeFrequencyDictionariesAreLoaded() {
        assertEquals("you're", WordLexicon.commonCorrection("en", "youre"))
        assertEquals("Bună", WordLexicon.commonCorrection("ro", "Buna"))
        assertEquals("mâine", WordLexicon.commonCorrection("ro", "maine"))
        assertNull(WordLexicon.commonCorrection("ro", "buna", restoreDiacritics = false))
        assertNull(WordLexicon.commonCorrection("en", "tEh"))
        assertNull(WordLexicon.commonCorrection("en", "user@email.com"))
        assertNull(WordLexicon.commonCorrection("en", "wrold"))
        assertNull(WordLexicon.commonCorrection("fr", "buna"))
    }
    @Test fun knownWordsAndPersonalNamesAreProtected() {
        assertNull(english.correction("cot")); assertNull(english.correction("teh", protected = setOf("teh")))
    }
    @Test fun caseIsPreservedWithoutChangingMixedCaseIdentifiers() {
        assertEquals("The", english.correction("Teh")); assertEquals("THE", english.correction("TEH"))
        assertNull(english.correction("tEh")); assertNull(english.correction("test123")); assertNull(english.correction("a@b"))
    }
    @Test fun romanianUsesCommaBelowAndCanRestoreDiacritics() {
        assertEquals("mulțumesc", romanian.correction("multumesc"))
        assertEquals("Bună", romanian.correction("Buna")); assertEquals("mâine", romanian.correction("maine"))
        assertTrue(romanian.contains("ŞI")); assertEquals("șț", WordLexicon.normalize("şţ"))
        assertNull(romanian.correction("multumesc", restoreDiacritics = false))
    }
    @Test fun suggestionsIncludeCompletionsAndExplicitPersonalWords() {
        assertTrue(english.suggestions("hel").contains("hello"))
        assertTrue(english.suggestions("ale", setOf("Alexandra")).contains("Alexandra"))
        assertNull(english.correction("hel"))
    }
    @Test fun longerWordsCanRecoverTwoEditsWithoutExpandingShortWordCorrections() {
        val lexicon = WordLexicon(listOf("keyboard" to 50000, "word" to 50000, "hello" to 50000), "en")
        assertEquals("keyboard", lexicon.correction("kryboafd"))
        assertTrue(lexicon.suggestions("kryboafd").contains("keyboard"))
        assertNull(lexicon.correction("wrad"))
        assertEquals("hello", lexicon.correction("helllo"))
    }
    @Test fun closeMatchesBeatDistantWordsAndAmbiguousCorrectionsStaySuggestions() {
        val lexicon = WordLexicon(listOf("candle" to 10000, "handle" to 10000, "cinder" to 100000), "en")
        assertEquals("candle", lexicon.suggestions("cindle").first())
        val ambiguous = WordLexicon(listOf("candle" to 10000, "handle" to 10000), "en")
        assertNull(ambiguous.correction("xandle"))
        assertEquals(setOf("candle", "handle"), ambiguous.suggestions("xandle").toSet())
        assertNull(WordLexicon(listOf("keyboard" to 1000), "en").correction("kryboafd"))
    }
    @Test fun englishPersonalWordsIncludeOneEditMatchesAndKeepCase() {
        assertTrue(english.suggestions("Alxeandra", setOf("Alexandra")).contains("Alexandra"))
    }
    @Test fun boundedDistanceAgreesWithFullDistanceIncludingTranspositions() {
        val values = listOf("", "a", "ab", "ba", "abc", "cab", "abcd", "badc", "keyboard", "kryboafd")
        for (first in values) for (second in values) for (limit in 0..2) {
            val full = MultilingualSuggestions.distance(first, second)
            val bounded = MultilingualSuggestions.distance(first, second, limit)
            if (full <= limit) assertEquals("$first / $second / $limit", full, bounded)
            else assertTrue(bounded > limit)
        }
    }
    @Test fun bundledDictionariesContainRealEnglishAndRomanianForms() {
        fun load(language: String): WordLexicon {
            val path = File("src/main/assets/dictionaries/$language.txt")
            val entries = path.readLines().mapNotNull { line ->
                val word = line.substringBeforeLast(' '); val count = line.substringAfterLast(' ').toIntOrNull()
                count?.let { word to it }
            }
            assertEquals(100000, entries.size)
            val supplement = File("src/main/assets/dictionaries/${language}_supplement.txt")
                .takeIf { it.exists() }?.readLines().orEmpty().map { it.substringBeforeLast(' ') to it.substringAfterLast(' ').toInt() }
            return WordLexicon(entries + supplement, language)
        }
        val en = load("en"); val ro = load("ro")
        assertEquals("the", en.correction("teh")); assertEquals("mulțumesc", ro.correction("multumesc"))
        assertTrue(ro.contains("română")); assertTrue(en.contains("keyboard"))
        assertTrue(en.contains("disclaimer")); assertNull(en.correction("disclaimer"))
        assertTrue(ro.contains("corporatistă")); assertNull(ro.correction("corporatistă"))
        val fixes = mapOf("definately" to "definitely", "tommorow" to "tomorrow", "neccessary" to "necessary",
            "becuase" to "because", "youre" to "you're", "thnaks" to "thanks", "keybaord" to "keyboard")
        fixes.forEach { (typo, correct) ->
            assertEquals(correct, en.immediateCorrection(typo))
            assertEquals(correct, en.correction(typo))
            assertEquals(correct, en.suggestions(typo).first())
        }
        mapOf("dimineata" to "dimineață", "sanatate" to "sănătate", "impreuna" to "împreună",
            "saptamana" to "săptămână", "intalnire" to "întâlnire").forEach { (plain, accented) ->
            assertEquals(accented, ro.immediateCorrection(plain))
            assertNull(ro.immediateCorrection(plain, restoreDiacritics = false))
            assertNull(ro.correction(plain, restoreDiacritics = false))
        }
        listOf("its", "were", "well", "ill", "shell", "form", "from").forEach { assertNull(en.correction(it)) }
        assertNull(en.correction("definately", protected = setOf("definately")))
        assertNull(en.correction("DeFiNaTeLy"))
        assertEquals("don't", en.immediateCorrection("dont"))
        assertNull(en.correction("you're"))
        assertTrue(en.contains("we're"))
    }
}

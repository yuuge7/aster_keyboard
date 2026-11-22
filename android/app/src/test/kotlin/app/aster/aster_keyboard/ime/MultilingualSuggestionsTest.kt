package app.aster.aster_keyboard.ime

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MultilingualSuggestionsTest {
    private fun engine(romanian: List<Pair<String, Int>>, english: List<Pair<String, Int>>): MultilingualSuggestions {
        val dictionaries = mapOf("ro" to WordLexicon(romanian, "ro"), "en" to WordLexicon(english, "en"))
        return MultilingualSuggestions { dictionaries[it] }
    }
    @Test fun bothLanguagesContributeAndRomanianWinsEqualMatches() {
        val suggestions = engine(listOf("cas" to 100), listOf("cat" to 100))
        assertEquals(listOf("cas", "cat"), suggestions.suggestions("cap", "ro"))
    }
    @Test fun completionsAndTyposUseFrequencyWithoutOverridingExactAccentMatches() {
        val suggestions = engine(listOf("helpfulness" to 1, "bună" to 1), listOf("help" to 100, "hello" to 50, "bun" to 100000))
        assertEquals(listOf("help", "hello", "helpfulness"), suggestions.suggestions("hel", "ro"))
        assertEquals("hello", suggestions.suggestions("hlelo", "ro").first())
        assertEquals("bună", suggestions.suggestions("buna", "ro").first())
    }
    @Test fun romanianDiacriticsAreIgnoredForCompletionsAndTypos() {
        val suggestions = engine(listOf("mulțumesc" to 1000, "bună" to 2000), listOf("bun" to 100000))
        assertTrue(suggestions.suggestions("multu", "ro").contains("mulțumesc"))
        assertEquals("mulțumesc", suggestions.suggestions("multumes", "ro").first())
        assertEquals("bună", suggestions.suggestions("buna", "ro").first())
    }
    @Test fun duplicatesAndTypedWordAreExcludedWhileCaseIsPreserved() {
        val suggestions = engine(listOf("hello" to 100, "help" to 100), listOf("hello" to 100, "help" to 100))
        assertEquals(listOf("Hello", "Help"), suggestions.suggestions("Hel", "ro"))
        assertEquals(listOf("HELLO", "HELP"), suggestions.suggestions("HEL", "ro"))
        assertFalse(suggestions.suggestions("hello", "ro").contains("hello"))
    }
    @Test fun bothPersonalDictionariesParticipateAndProtectTheirSpelling() {
        val suggestions = engine(listOf("maria" to 100000), listOf("mark" to 100000))
        val personal = mapOf("ro" to setOf("Mara"), "en" to setOf("Marv"))
        assertEquals(listOf("Mara", "Marv"), suggestions.suggestions("mar", "ro", personal).take(2))
        assertTrue(suggestions.suggestions("mrav", "ro", personal).contains("Marv"))
    }
    @Test fun englishKeepsItsExistingSuggestions() {
        val en = WordLexicon(listOf("hello" to 1000, "help" to 100), "en")
        val ro = WordLexicon(listOf("helă" to 1000000), "ro")
        val suggestions = MultilingualSuggestions { if (it == "en") en else ro }
        assertEquals(en.suggestions("hel"), suggestions.suggestions("hel", "en"))
        assertFalse(suggestions.suggestions("hel", "en").contains("helă"))
    }
    @Test fun recognizedEnglishWordsAreProtectedWithoutBlockingRomanianRestoration() {
        val suggestions = engine(listOf("bună" to 1000, "mâine" to 1000),
            listOf("cot" to 1, "buna" to 1, "maine" to 1))
        assertTrue(suggestions.protectsEnglish("cot", "ro"))
        assertFalse(suggestions.protectsEnglish("buna", "ro"))
        assertFalse(suggestions.protectsEnglish("maine", "ro"))
        assertTrue(suggestions.protectsEnglish("maine", "ro", restoreDiacritics = false))
        assertFalse(suggestions.protectsEnglish("cot", "en"))
    }
    @Test fun coldRomanianDictionaryDoesNotLetRareEnglishFormsBlockCuratedAccents() {
        val en = WordLexicon(listOf("buna" to 67, "maine" to 2143, "keyboard" to 10000), "en")
        val suggestions = MultilingualSuggestions { if (it == "en") en else null }
        assertFalse(suggestions.protectsEnglish("buna", "ro"))
        assertFalse(suggestions.protectsEnglish("maine", "ro"))
        assertTrue(suggestions.protectsEnglish("buna", "ro", restoreDiacritics = false))
        assertTrue(suggestions.protectsEnglish("keyboard", "ro"))
    }
    @Test fun missingDictionaryAndInvalidInputStayUsable() {
        val suggestions = MultilingualSuggestions { if (it == "en") WordLexicon(listOf("hello" to 100), "en") else null }
        assertTrue(suggestions.suggestions("hel", "ro").contains("hello"))
        assertTrue(suggestions.suggestions("", "ro").isEmpty())
        assertTrue(suggestions.suggestions("x".repeat(33), "ro").isEmpty())
    }
    @Test fun romanianModeCorrectsEnglishTyposAndKeepsKnownRomanianWords() {
        val suggestions = engine(listOf("bună" to 100000, "cort" to 20000),
            listOf("world" to 100000, "keyboard" to 100000, "court" to 100000, "the" to 100000))
        assertEquals("world", suggestions.correction("wrold", "ro"))
        assertEquals("keyboard", suggestions.correction("keybord", "ro"))
        assertEquals("the", suggestions.immediateCorrection("teh", "ro"))
        assertNull(suggestions.correction("cort", "ro"))
        assertEquals("Bună", suggestions.correction("Buna", "ro"))
        assertNull(suggestions.correction("world", "ro"))
    }
    @Test fun equallyCloseCrossLanguageCorrectionsAreNotAppliedAutomatically() {
        val suggestions = engine(listOf("candle" to 100000), listOf("handle" to 100000))
        assertNull(suggestions.correction("xandle", "ro"))
        assertTrue(suggestions.suggestions("xandle", "ro").containsAll(listOf("candle", "handle")))
    }
    @Test fun bundledDictionariesSuggestRealMixedLanguageWords() {
        val dictionaries = listOf("ro", "en").associateWith { language ->
            val entries = File("src/main/assets/dictionaries/$language.txt").readLines().mapNotNull { line ->
                line.substringAfterLast(' ').toIntOrNull()?.let { line.substringBeforeLast(' ') to it }
            }
            WordLexicon(entries, language)
        }
        val suggestions = MultilingualSuggestions { dictionaries[it] }
        assertEquals("keyboard", suggestions.suggestions("keybord", "ro").first())
        assertTrue(suggestions.suggestions("multu", "ro").contains("mulțumesc"))
        assertEquals("mulțumesc", suggestions.suggestions("multumesc", "ro").first())
        assertFalse(suggestions.protectsEnglish("buna", "ro"))
        assertFalse(suggestions.protectsEnglish("maine", "ro"))
        assertEquals("world", suggestions.correction("wrold", "ro"))
        assertEquals("keyboard", suggestions.correction("keyboarf", "ro"))
        assertEquals("the", suggestions.correction("teh", "ro"))
        assertEquals("receive", suggestions.correction("recieve", "ro"))
        assertNull(suggestions.correction("adn", "ro")) // Romanian abbreviation for DNA.
        assertEquals("mulțumesc", suggestions.correction("multumesc", "ro"))
        assertNull(suggestions.correction("corporatistă", "ro"))
    }
}

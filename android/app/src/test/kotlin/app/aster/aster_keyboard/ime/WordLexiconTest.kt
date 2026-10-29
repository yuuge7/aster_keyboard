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
    @Test fun bundledDictionariesContainRealEnglishAndRomanianForms() {
        fun load(language: String): WordLexicon {
            val path = File("src/main/assets/dictionaries/$language.txt")
            val entries = path.readLines().mapNotNull { line ->
                val word = line.substringBeforeLast(' '); val count = line.substringAfterLast(' ').toIntOrNull()
                count?.let { word to it }
            }
            assertEquals(50000, entries.size)
            return WordLexicon(entries, language)
        }
        val en = load("en"); val ro = load("ro")
        assertEquals("the", en.correction("teh")); assertEquals("mulțumesc", ro.correction("multumesc"))
        assertTrue(ro.contains("română")); assertTrue(en.contains("keyboard"))
    }
}

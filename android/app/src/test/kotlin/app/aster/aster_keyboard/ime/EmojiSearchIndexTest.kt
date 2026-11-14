package app.aster.aster_keyboard.ime

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class EmojiSearchIndexTest {
    private val index by lazy {
        File("src/main/assets/emoji/search.tsv").useLines { EmojiSearchIndex(it) }
    }
    @Test fun everyCatalogEmojiHasBilingualMetadataAndCanBeFoundByItsNames() {
        val rows = File("src/main/assets/emoji/search.tsv").readLines().map { it.split('\t') }
        val catalog = EmojiCatalog.categories.values.flatten()
        assertEquals(catalog.size, rows.size)
        assertEquals(catalog.toSet(), rows.map { it[0] }.toSet())
        rows.forEach { row ->
            assertEquals(4, row.size)
            assertTrue(row[1].isNotBlank()); assertTrue(row[2].isNotBlank())
            assertTrue("Missing English name: ${row[1]}", index.search(row[1]).contains(row[0]))
            assertTrue("Missing Romanian name: ${row[2]}", index.search(row[2]).contains(row[0]))
        }
    }
    @Test fun searchesBothLanguagesRegardlessOfAccentsCaseOrShortcodePunctuation() {
        assertEquals("❤️", index.search("red heart").first())
        assertEquals("❤️", index.search("heart").first())
        assertTrue(index.search("INIMĂ").contains("❤️"))
        assertEquals(index.search("inimă"), index.search("inima"))
        assertTrue(index.search(":thumbs_up:").contains("👍"))
        assertTrue(index.search("LOL").contains("😂"))
        assertTrue(index.search("cafea").contains("☕"))
    }
    @Test fun matchesPrefixesAllQueryWordsFlagsAndWholeEmojiSequences() {
        assertTrue(index.search("red hea").contains("❤️"))
        assertTrue(index.search("romania").contains("🇷🇴"))
        assertTrue(index.search("Romania flag").contains("🇷🇴"))
        assertEquals(listOf("❤️‍🔥"), index.search("❤️‍🔥"))
        assertTrue(index.search("heart").distinct() == index.search("heart"))
        assertTrue(index.search("red coffee").isEmpty())
    }
    @Test fun blankPunctuationUnknownAndOversizedQueriesHaveNoMatches() {
        listOf("", "  ", "::", "zzzzunfindable", "a".repeat(65)).forEach { assertTrue(index.search(it).isEmpty()) }
    }
    @Test fun interruptedSearchStopsWithoutPublishingPartialMatches() {
        try {
            Thread.currentThread().interrupt()
            assertTrue(index.search("heart").isEmpty())
        } finally { Thread.interrupted() }
    }
    @Test fun searchTextCanBeEditedAndClearedWithoutSplittingUnicode() {
        val session = EmojiSearchSession()
        session.append("red heart")
        session.backspace(); assertEquals("red hear", session.query)
        session.clear(); session.backspace(); assertEquals("", session.query)
        session.append("a😀"); session.backspace(); assertEquals("a", session.query)
        session.append("\n\t"); assertEquals("a", session.query)
        session.append("x".repeat(100)); assertEquals(64, session.query.length)
        session.clear(); session.append("a".repeat(63) + "😀")
        assertEquals("a".repeat(63), session.query)
    }
}

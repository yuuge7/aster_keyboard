package app.aster.aster_keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class TypingSessionTest {
    private class Editor : TextEditor {
        var text = ""; private var start = -1
        override fun compose(text: String) { if (start < 0) start = this.text.length; this.text = this.text.take(start) + text }
        override fun commit(text: String) { if (start >= 0) this.text = this.text.take(start); this.text += text; start = -1 }
        override fun finish() { start = -1 }
        override fun before(length: Int) = text.takeLast(length)
        override fun replaceBefore(length: Int, text: String) { finish(); this.text = this.text.dropLast(length) + text }
    }
    private fun type(session: TypingSession, text: String, punctuation: Boolean = true, doubleSpace: Boolean = true, correction: (String) -> String? = { null }) {
        text.forEachIndexed { index, char -> session.type(char.toString(), true, correction, punctuation, doubleSpace, index * 100L) }
    }
    @Test fun correctsAtWordBoundaryAndBackspaceRestoresOriginal() {
        val editor = Editor(); val session = TypingSession(editor)
        type(session, "teh ", correction = { if (it == "teh") "the" else null })
        assertEquals("the ", editor.text); assertTrue(session.backspace()); assertEquals("teh", editor.text)
        assertEquals("teh", session.word)
    }
    @Test fun punctuationInsertsOneSpaceWithoutDuplicatingManualSpace() {
        val editor = Editor(); val session = TypingSession(editor)
        type(session, "hello, world")
        assertEquals("hello, world", editor.text)
    }
    @Test fun punctuationSpacingCanBeDisabled() {
        val editor = Editor(); type(TypingSession(editor), "hello,world!", punctuation = false)
        assertEquals("hello,world!", editor.text)
    }
    @Test fun punctuationSequencesDoNotAccumulateSpaces() {
        val editor = Editor(); type(TypingSession(editor), "what!?")
        assertEquals("what!? ", editor.text)
    }
    @Test fun fastDoubleSpaceInsertsPeriod() {
        val editor = Editor(); type(TypingSession(editor), "hello  ")
        assertEquals("hello. ", editor.text)
    }
    @Test fun slowDoubleSpaceAndDisabledSettingPreserveSpaces() {
        val editor = Editor(); val session = TypingSession(editor)
        type(session, "hello ")
        session.type(" ", true, { null }, true, true, 2000)
        assertEquals("hello  ", editor.text)
        val other = Editor(); type(TypingSession(other), "hello  ", doubleSpace = false)
        assertEquals("hello  ", other.text)
    }
    @Test fun numbersAndExistingPunctuationDoNotBecomePeriods() {
        val editor = Editor(); type(TypingSession(editor), "hello.  ")
        assertEquals("hello.  ", editor.text)
    }
    @Test fun deletingCompositionPreservesSurroundingText() {
        val editor = Editor().apply { text = "before " }; val session = TypingSession(editor)
        type(session, "română"); assertTrue(session.backspace()); assertEquals("before român", editor.text)
    }
    @Test fun resetClearsUndoWhenCursorOrEditorChanges() {
        val editor = Editor(); val session = TypingSession(editor)
        type(session, "teh ", correction = { "the" }); session.reset()
        assertFalse(session.backspace()); assertEquals("the ", editor.text); assertEquals("", session.word)
    }
    @Test fun acceptingSuggestionReplacesCompositionAndAddsSpace() {
        val editor = Editor(); val session = TypingSession(editor)
        type(session, "mul"); session.accept("mulțumesc")
        assertEquals("mulțumesc ", editor.text); assertEquals("", session.word)
    }
    @Test fun disablingCompositionCommitsOrdinaryText() {
        val editor = Editor(); val session = TypingSession(editor)
        session.type("a", false, { "b" }, false, false, 0)
        assertEquals("a", editor.text); assertEquals("", session.word)
    }
    @Test fun enterCanFinishAndCorrectTheWordWithoutInsertingANewline() {
        val editor = Editor(); val session = TypingSession(editor)
        type(session, "teh"); session.finishWord { "the" }
        assertEquals("the", editor.text); assertEquals("", session.word)
    }
    @Test fun punctuationSpacingPreservesDecimalCommasAndTimes() {
        val editor = Editor(); type(TypingSession(editor), "1,5 lei 12:30")
        assertEquals("1,5 lei 12:30", editor.text)
    }
}

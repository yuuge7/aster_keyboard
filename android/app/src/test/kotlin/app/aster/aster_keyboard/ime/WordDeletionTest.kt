package app.aster.aster_keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class WordDeletionTest {
    @Test fun removesTrailingSpaceAndOneWholeWord() {
        assertEquals(6, WordDeletion.length("hello world "))
        assertEquals(5, WordDeletion.length("hello world"))
        assertEquals(0, WordDeletion.length(""))
        assertEquals(3, WordDeletion.length(" \n "))
        assertEquals(10, WordDeletion.length("hi don't-stop"))
    }
    @Test fun respectsUnicodeAndPunctuationBoundaries() {
        assertEquals(9, WordDeletion.length("salut mulțumesc"))
        assertEquals(5, WordDeletion.length("cafe\u0301"))
        assertEquals(5, WordDeletion.length("hi 👩‍💻"))
        assertEquals(2, WordDeletion.length("hello!?"))
    }
}

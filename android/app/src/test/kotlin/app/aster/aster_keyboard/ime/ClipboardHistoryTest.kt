package app.aster.aster_keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class ClipboardHistoryTest {
    @Test fun expiryRemovesOnlyUnpinnedClipsAndUnpinStartsANewHour() {
        var time = 1000L
        val history = ClipboardHistory(now = { time })
        history.record("temporary"); history.record("saved"); history.togglePin("saved")
        time += ClipboardHistory.EXPIRY_MILLIS
        assertEquals(listOf("saved"), history.entries().map { it.text })
        history.togglePin("saved")
        time += ClipboardHistory.EXPIRY_MILLIS - 1
        assertEquals(1, history.entries().size)
        time++
        assertTrue(history.entries().isEmpty())
    }
    @Test fun repeatedCopyPreservesExactWhitespaceAndPinWithoutDuplicates() {
        var time = 10L
        val history = ClipboardHistory(now = { time })
        history.record("  hello,\nworld!  "); history.togglePin("  hello,\nworld!  ")
        time++
        history.record("  hello,\nworld!  ")
        assertEquals(listOf(ClipboardEntry("  hello,\nworld!  ", time, true)), history.entries())
    }
    @Test fun capacityEvictsOldestUnpinnedAndNeverEvictsPinnedClips() {
        var time = 0L
        val history = ClipboardHistory(now = { time++ })
        history.record("pinned"); history.togglePin("pinned")
        repeat(30) { history.record("clip $it") }
        assertEquals(20, history.entries().size)
        assertEquals("pinned", history.entries().first().text)
        assertFalse(history.entries().any { it.text == "clip 0" })
        history.entries().forEach { if (!it.pinned) history.togglePin(it.text) }
        history.record("no room")
        assertFalse(history.entries().any { it.text == "no room" })
    }
    @Test fun rejectsOversizedAndBlankTextAndSupportsDeleteAndClear() {
        val history = ClipboardHistory()
        history.record(" \n "); history.record("x".repeat(4001))
        assertTrue(history.entries().isEmpty())
        history.record("first"); history.record("second")
        history.remove("first")
        assertEquals(listOf("second"), history.entries().map { it.text })
        history.clear()
        assertTrue(history.entries().isEmpty())
    }
    @Test fun reloadingPrunesExpiredEntriesAndKeepsPinnedClips() {
        val history = ClipboardHistory(listOf(ClipboardEntry("expired", 0), ClipboardEntry("pinned", 0, true))) {
            ClipboardHistory.EXPIRY_MILLIS
        }
        assertEquals(listOf("pinned"), history.entries().map { it.text })
    }
}

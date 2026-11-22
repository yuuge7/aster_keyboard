package app.aster.aster_keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class InputDispatchQueueTest {
    @Test fun delayedCorrectionPrecedesFollowingLettersDeletionAndEnter() {
        val queue = InputDispatchQueue(); val output = mutableListOf<String>()
        var token = 0L
        queue.submit { output.add("wrold"); token = queue.pause() }
        queue.submit { output.add("next letter") }
        queue.submit { output.add("backspace") }
        queue.submit { output.add("enter") }
        assertEquals(listOf("wrold"), output)
        queue.resume(token) { output.add("world ") }
        assertEquals(listOf("wrold", "world ", "next letter", "backspace", "enter"), output)
    }
    @Test fun editorChangeDiscardsOldKeysAndLateCorrection() {
        val queue = InputDispatchQueue(); var token = 0L; val output = mutableListOf<String>()
        queue.submit { token = queue.pause() }
        queue.submit { output.add("old field") }
        queue.clear()
        queue.submit { output.add("new field") }
        queue.resume(token) { output.add("stale correction") }
        assertEquals(listOf("new field"), output)
    }
    @Test fun consecutiveBoundariesAndReentrantEventsKeepOrder() {
        val queue = InputDispatchQueue(); val output = mutableListOf<Int>(); var first = 0L; var second = 0L
        queue.submit { first = queue.pause() }
        queue.submit { output.add(2); second = queue.pause() }
        queue.submit { output.add(4); queue.submit { output.add(5) } }
        queue.resume(first) { output.add(1) }
        assertEquals(listOf(1, 2), output)
        queue.resume(first) { fail("Duplicate completion must be ignored") }
        queue.resume(second) { output.add(3) }
        assertEquals(listOf(1, 2, 3, 4, 5), output)
    }
}

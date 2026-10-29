package app.aster.aster_keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class SelectionTrackerTest {
    @Test fun lateWordBoundaryAcknowledgementDoesNotCancelTheNextComposingWord() {
        val tracker = SelectionTracker().apply { reset(0, 0) }
        tracker.compose(3); tracker.commit(3); tracker.commit(1); tracker.compose(2)
        assertTrue(tracker.update(3, 3, -1, -1))
        assertTrue(tracker.update(4, 4, -1, -1))
        assertTrue(tracker.update(6, 6, 4, 6))
        tracker.compose(3)
        assertTrue(tracker.update(7, 7, 4, 7))
    }
    @Test fun realCursorMovementAndSelectionsRemainExternal() {
        val tracker = SelectionTracker().apply { reset(10, 10) }
        tracker.compose(4)
        assertFalse(tracker.update(2, 2, -1, -1))
        tracker.compose(1)
        assertTrue(tracker.update(3, 3, 2, 3))
        assertFalse(tracker.update(0, 3, -1, -1))
        tracker.commit(5)
        assertTrue(tracker.update(5, 5, -1, -1))
    }
    @Test fun coalescedAndDuplicateAcksKeepTheLatestPredictedCursor() {
        val tracker = SelectionTracker().apply { reset(0, 0) }
        tracker.compose(1); tracker.compose(2); tracker.compose(3)
        assertTrue(tracker.update(3, 3, 0, 3))
        tracker.compose(4)
        assertTrue(tracker.update(2, 2, 0, 2))
        assertTrue(tracker.update(4, 4, 0, 4))
        assertFalse(tracker.update(2, 2, 0, 2))
    }
    @Test fun correctionsUndoAndNewEditorsUseIndependentOffsets() {
        val tracker = SelectionTracker().apply { reset(7, 7) }
        tracker.compose(3); tracker.commit(5); tracker.commit(1)
        assertTrue(tracker.update(13, 13, -1, -1))
        tracker.deleteBefore(6); tracker.compose(3)
        assertTrue(tracker.update(10, 10, 7, 10))
        tracker.reset(0, 0); tracker.compose(1)
        assertTrue(tracker.update(1, 1, 0, 1))
        assertFalse(tracker.update(10, 10, 7, 10))
    }
    @Test fun tappingInsideTheCurrentWordIsNotMistakenForAnOldCompositionAck() {
        val tracker = SelectionTracker().apply { reset(0, 0) }
        tracker.compose(1); tracker.compose(2); tracker.compose(3); tracker.compose(4); tracker.compose(5)
        assertFalse(tracker.update(2, 2, 0, 5))
        tracker.finishComposition(); tracker.commit(1)
        assertTrue(tracker.update(3, 3, -1, -1))
    }
}

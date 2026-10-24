package app.aster.aster_keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class ShiftStateTest {
    @Test fun oneShotIsConsumed() {
        val state = ShiftState()
        state.tap(1000)
        assertTrue(state.uppercase)
        state.consume()
        assertEquals(ShiftMode.OFF, state.mode)
    }
    @Test fun doubleTapLocksUntilTappedAgain() {
        val state = ShiftState()
        state.tap(1000); state.tap(1200); state.consume()
        assertEquals(ShiftMode.LOCKED, state.mode)
        state.tap(1300)
        assertEquals(ShiftMode.OFF, state.mode)
    }
    @Test fun aSlowSecondTapDisablesShift() {
        val state = ShiftState()
        state.tap(1000); state.tap(1600)
        assertEquals(ShiftMode.OFF, state.mode)
    }
    @Test fun changingEditorClearsLockAndTapHistory() {
        val state = ShiftState()
        state.lock(); state.reset(); state.tap(1300)
        assertEquals(ShiftMode.ONCE, state.mode)
    }
    @Test fun automaticCapitalizationPreservesCapsLock() {
        val state = ShiftState()
        state.lock(); state.autoCap(false)
        assertEquals(ShiftMode.LOCKED, state.mode)
    }
    @Test fun manualLowercaseOverridesAutomaticCapitalizationForOneCharacter() {
        val state = ShiftState()
        state.autoCap(true); state.tap(1000); state.autoCap(true)
        assertEquals(ShiftMode.OFF, state.mode)
        state.consume(); state.autoCap(true)
        assertEquals(ShiftMode.ONCE, state.mode)
    }
    @Test fun manualShiftStillWorksWithAutomaticCapitalizationDisabled() {
        val state = ShiftState()
        state.tap(1000); state.autoCap(false)
        assertEquals(ShiftMode.ONCE, state.mode)
        state.consume(); assertEquals(ShiftMode.OFF, state.mode)
    }
}

package app.aster.aster_keyboard.ime

import app.aster.aster_keyboard.settings.KeyboardSettings
import app.aster.aster_keyboard.settings.SettingsConstraints
import org.junit.Assert.*
import org.junit.Test

class SettingsConstraintsTest {
    @Test fun eachIntegerPreferenceUsesItsOwnRange() {
        assertEquals(66, SettingsConstraints.integer("keyHeight", 999))
        assertEquals(0, SettingsConstraints.integer("keySpacing", -1))
        assertEquals(5, SettingsConstraints.integer("keySpacing", 999))
        assertEquals(0, SettingsConstraints.integer("keyCornerRadius", -1))
        assertEquals(18, SettingsConstraints.integer("keyCornerRadius", 999))
        assertEquals(200, SettingsConstraints.integer("longPressDelay", 0))
        assertEquals(800, SettingsConstraints.integer("longPressDelay", 999))
    }
    @Test fun ordinaryValuesArePreserved() {
        assertEquals(3, SettingsConstraints.integer("keySpacing", 3))
        assertEquals(12, SettingsConstraints.integer("keyCornerRadius", 12.0))
        assertEquals(650, SettingsConstraints.integer("longPressDelay", 650L))
    }
    @Test fun nonFiniteNumbersAreRejected() {
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { invalid ->
            try { SettingsConstraints.integer("keySpacing", invalid); fail("Non-finite value accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun unknownIntegerKeysAreRejected() {
        try { SettingsConstraints.integer("wallpaperDim", 1); fail("Unknown integer key accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun defaultsAreBackwardCompatibleAndIncludedInTheBridgeSnapshot() {
        val defaults = KeyboardSettings()
        val values = defaults.toMap()
        assertEquals(2, values["keySpacing"])
        assertEquals(8, values["keyCornerRadius"])
        assertEquals(false, values["keyBorders"])
        assertEquals(true, values["secondarySymbols"])
        assertEquals(400, values["longPressDelay"])
    }
}

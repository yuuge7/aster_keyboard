package app.aster.aster_keyboard.ime

enum class ShiftMode { OFF, ONCE, LOCKED }

class ShiftState {
    var mode = ShiftMode.OFF
        private set
    private var lastTap = -1000L
    private var manual = false
    val uppercase get() = mode != ShiftMode.OFF
    fun tap(now: Long) {
        manual = true
        mode = when {
            mode == ShiftMode.LOCKED -> ShiftMode.OFF
            now - lastTap in 0..350 -> ShiftMode.LOCKED
            mode == ShiftMode.ONCE -> ShiftMode.OFF
            else -> ShiftMode.ONCE
        }
        lastTap = now
    }
    fun lock() { mode = ShiftMode.LOCKED; manual = true }
    fun consume() { if (mode == ShiftMode.ONCE) mode = ShiftMode.OFF; if (mode != ShiftMode.LOCKED) manual = false }
    fun reset() { mode = ShiftMode.OFF; lastTap = -1000L; manual = false }
    fun autoCap(enabled: Boolean) { if (mode != ShiftMode.LOCKED && !manual) mode = if (enabled) ShiftMode.ONCE else ShiftMode.OFF }
}

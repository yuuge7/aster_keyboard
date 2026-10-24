package app.aster.aster_keyboard.ime

enum class ShiftMode { OFF, ONCE, LOCKED }

class ShiftState {
    var mode = ShiftMode.OFF
        private set
    private var lastTap = -1000L
    val uppercase get() = mode != ShiftMode.OFF
    fun tap(now: Long) {
        mode = when {
            mode == ShiftMode.LOCKED -> ShiftMode.OFF
            now - lastTap in 0..350 -> ShiftMode.LOCKED
            mode == ShiftMode.ONCE -> ShiftMode.OFF
            else -> ShiftMode.ONCE
        }
        lastTap = now
    }
    fun lock() { mode = ShiftMode.LOCKED }
    fun consume() { if (mode == ShiftMode.ONCE) mode = ShiftMode.OFF }
    fun reset() { mode = ShiftMode.OFF; lastTap = -1000L }
    fun autoCap(enabled: Boolean) { if (mode != ShiftMode.LOCKED) mode = if (enabled) ShiftMode.ONCE else ShiftMode.OFF }
}

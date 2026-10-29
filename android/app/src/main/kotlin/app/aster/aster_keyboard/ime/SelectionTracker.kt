package app.aster.aster_keyboard.ime

/** Only cursor offsets are tracked. No field contents are read or retained. */
class SelectionTracker {
    private data class Position(val start: Int, val end: Int, val composingStart: Int = -1, val composingEnd: Int = -1)
    private var cursor = Position(-1, -1)
    private val pending = ArrayDeque<Position>()
    private val acknowledged = ArrayDeque<Position>()
    fun reset(start: Int = -1, end: Int = -1) {
        cursor = Position(start, end); pending.clear(); acknowledged.clear()
    }
    private fun record() {
        if (cursor.start < 0 || cursor.end < 0) return
        if (pending.lastOrNull() != cursor) pending.addLast(cursor)
        while (pending.size > 256) pending.removeFirst()
    }
    fun compose(length: Int) {
        if (cursor.start < 0 || cursor.end < 0) return
        val start = cursor.composingStart.takeIf { it >= 0 } ?: minOf(cursor.start, cursor.end)
        cursor = Position(start + length, start + length, start, start + length); record()
    }
    fun commit(length: Int) {
        if (cursor.start < 0 || cursor.end < 0) return
        val start = cursor.composingStart.takeIf { it >= 0 } ?: minOf(cursor.start, cursor.end)
        cursor = Position(start + length, start + length); record()
    }
    fun finishComposition() { cursor = cursor.copy(composingStart = -1, composingEnd = -1); record() }
    fun deleteBefore(length: Int) {
        if (cursor.start < 0 || cursor.start != cursor.end) { reset(); return }
        val start = (cursor.start - length).coerceAtLeast(0)
        cursor = Position(start, start); record()
    }
    /** Acknowledgements may be delayed/coalesced across several words. */
    fun update(start: Int, end: Int, candidatesStart: Int, candidatesEnd: Int): Boolean {
        val position = Position(start, end, candidatesStart, candidatesEnd)
        val index = pending.indexOfLast { it == position }
        if (index >= 0) {
            repeat(index + 1) { acknowledged.addLast(pending.removeFirst()) }
            while (acknowledged.size > 32) acknowledged.removeFirst()
            return true
        }
        if (position == cursor || pending.isNotEmpty() && position in acknowledged) return true
        reset(start, end); cursor = position
        return false
    }
}

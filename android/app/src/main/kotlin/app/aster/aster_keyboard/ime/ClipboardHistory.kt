package app.aster.aster_keyboard.ime

data class ClipboardEntry(val text: String, val copiedAt: Long, val pinned: Boolean = false)

/** Only explicitly copied text enters this bounded history; never editor contents. */
class ClipboardHistory(initial: List<ClipboardEntry> = emptyList(), private val now: () -> Long = System::currentTimeMillis) {
    companion object {
        const val MAX_ENTRIES = 20
        const val MAX_TEXT_LENGTH = 4000
        const val EXPIRY_MILLIS = 60 * 60 * 1000L
    }
    private val items = initial.filter { valid(it.text) }.distinctBy { it.text }.take(MAX_ENTRIES).toMutableList()
    private fun valid(text: String) = text.isNotBlank() && text.length <= MAX_TEXT_LENGTH
    fun entries(): List<ClipboardEntry> {
        val time = now()
        items.removeAll { !it.pinned && time - it.copiedAt >= EXPIRY_MILLIS }
        return items.sortedWith(compareByDescending<ClipboardEntry> { it.pinned }.thenByDescending { it.copiedAt })
    }
    fun record(text: String) {
        if (!valid(text)) return
        entries()
        val previous = items.firstOrNull { it.text == text }
        if (previous == null && items.size >= MAX_ENTRIES) {
            val oldest = items.filter { !it.pinned }.minByOrNull { it.copiedAt } ?: return
            items.remove(oldest)
        }
        items.removeAll { it.text == text }
        items.add(ClipboardEntry(text, now(), previous?.pinned == true))
    }
    fun togglePin(text: String) {
        entries()
        val index = items.indexOfFirst { it.text == text }
        if (index >= 0) items[index] = items[index].copy(pinned = !items[index].pinned, copiedAt = now())
    }
    fun remove(text: String) { items.removeAll { it.text == text } }
    fun clear() { items.clear() }
}

package app.aster.aster_keyboard.ime

/** Explicit delete-word action: spaces plus one word or punctuation group. */
internal object WordDeletion {
    fun length(before: String): Int {
        var end = before.length
        while (end > 0 && before.codePointBefore(end).let { Character.isWhitespace(it) }) {
            end -= Character.charCount(before.codePointBefore(end))
        }
        if (end == 0) return before.length
        fun word(code: Int) = Character.isLetterOrDigit(code) || code in listOf(39, 0x2019, 45) ||
            Character.getType(code) in listOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt())
        val isWord = word(before.codePointBefore(end))
        do {
            end -= Character.charCount(before.codePointBefore(end))
        } while (end > 0 && !Character.isWhitespace(before.codePointBefore(end)) && word(before.codePointBefore(end)) == isWord)
        // A bounded context must never split a surrogate pair at its left edge.
        if (end < before.length && Character.isLowSurrogate(before[end])) end++
        return before.length - end
    }
}
